import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * 端口扫描器 - 基于Java的多线程TCP端口扫描工具
 * 功能：扫描指定IP地址或IP段的指定端口范围，检测端口开放状态
 */
public class PortScanner {
    // 扫描结果输出控制：是否只显示开放端口
    private static boolean onlyShowOpenPorts = true;
    // 每个线程负责扫描的端口数量
    private static final int PORTS_PER_THREAD = 100;
    // 连接超时时间（毫秒）
    private static final int TIMEOUT = 500;
    
    /**
     * 主方法 - 程序入口
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("===== Port Scanner - Multi-threaded TCP Port Scanning Tool =====");
        
        try {
            // 获取用户输入的扫描参数
            String startIp = getInput(scanner, "Enter start IP address:");
            String endIp = getInput(scanner, "Enter end IP address (if scanning a single IP, enter the same address):");
            int startPort = Integer.parseInt(getInput(scanner, "Enter start port:"));
            int endPort = Integer.parseInt(getInput(scanner, "Enter end port:"));
            onlyShowOpenPorts = Boolean.parseBoolean(getInput(scanner, "Show only open ports (true/false):"));
            
            // 计算需要扫描的端口总数
            int totalPorts = endPort - startPort + 1;
            if (totalPorts <= 0) {
                System.out.println("Invalid port range. Ensure the end port is greater than or equal to the start port.");
                return;
            }
            
            // 计算需要启动的线程数量
            int threadCount = (int) Math.ceil((double) totalPorts / PORTS_PER_THREAD);
            System.out.println("Based on the workload, " + threadCount + " threads will be started for scanning...");
            
            // 解析IP地址范围
            long[] ipRange = parseIpRange(startIp, endIp);
            if (ipRange == null) {
                System.out.println("Failed to parse IP range. Check input format.");
                return;
            }
            long startIpNum = ipRange[0];
            long endIpNum = ipRange[1];
            int ipCount = (int) (endIpNum - startIpNum + 1);
            
            System.out.println("Starting scan on " + ipCount + " IP addresses, port range: " + startPort + " - " + endPort);
            
            // 对每个IP地址进行端口扫描
            for (long ipNum = startIpNum; ipNum <= endIpNum; ipNum++) {
                String currentIp = ipNumToAddress(ipNum);
                scanIpAddress(currentIp, startPort, endPort, threadCount);
            }
            
        } catch (NumberFormatException e) {
            System.out.println("Input format error. Ensure port numbers are integers.");
        } catch (Exception e) {
            System.out.println("Error during scanning: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
    
    /**
     * 扫描指定IP地址的端口范围
     * @param ipAddress 目标IP地址
     * @param startPort 起始端口
     * @param endPort 终止端口
     * @param threadCount 线程数量
     */
    private static void scanIpAddress(String ipAddress, int startPort, int endPort, int threadCount) {
        System.out.println("\nStarting scan on IP: " + ipAddress + ", port range: " + startPort + " - " + endPort);
        
        // 使用CountDownLatch等待所有线程完成
        CountDownLatch latch = new CountDownLatch(threadCount);
        int portsPerThread = (endPort - startPort + 1 + threadCount - 1) / threadCount; // 向上取整
        
        // 创建并启动扫描线程
        for (int i = 0; i < threadCount; i++) {
            int threadStartPort = startPort + i * portsPerThread;
            int threadEndPort = Math.min(threadStartPort + portsPerThread - 1, endPort);
            
            if (threadStartPort > endPort) {
                break;
            }
            
            PortScanThread thread = new PortScanThread(ipAddress, threadStartPort, threadEndPort, latch);
            thread.start();
        }
        
        try {
            // 等待所有线程完成扫描
            latch.await();
            System.out.println("Scan completed for IP " + ipAddress);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Scan interrupted: " + e.getMessage());
        }
    }
    
    /**
     * 解析IP地址范围为数字范围
     * @param startIp 起始IP地址
     * @param endIp 终止IP地址
     * @return 包含起始和终止IP数字表示的数组，失败返回null
     */
    private static long[] parseIpRange(String startIp, String endIp) {
        try {
            long start = ipAddressToNum(startIp);
            long end = ipAddressToNum(endIp);
            
            if (start > end) {
                long temp = start;
                start = end;
                end = temp;
            }
            
            return new long[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 将IP地址转换为长整型数字表示
     * @param ipAddress IP地址字符串
     * @return 长整型IP地址
     */
    private static long ipAddressToNum(String ipAddress) {
        String[] ipSegments = ipAddress.split("\\.");
        if (ipSegments.length != 4) {
            throw new IllegalArgumentException("Invalid IP address format");
        }
        
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int segment = Integer.parseInt(ipSegments[i]);
            result |= (segment << (24 - i * 8));
        }
        return result;
    }
    
    /**
     * 将长整型IP地址转换为字符串表示
     * @param ipNum 长整型IP地址
     * @return IP地址字符串
     */
    private static String ipNumToAddress(long ipNum) {
        return ((ipNum >> 24) & 0xFF) + "." +
               ((ipNum >> 16) & 0xFF) + "." +
               ((ipNum >> 8) & 0xFF) + "." +
               (ipNum & 0xFF);
    }
    
    /**
     * 获取用户输入
     * @param scanner 扫描器对象
     * @param prompt 提示信息
     * @return 用户输入的字符串
     */
    private static String getInput(Scanner scanner, String prompt) {
        System.out.print(prompt + " ");
        return scanner.nextLine().trim();
    }
    
    /**
     * 端口扫描线程类 - 负责扫描指定IP的部分端口范围
     */
    static class PortScanThread extends Thread {
        private String ipAddress;
        private int startPort;
        private int endPort;
        private CountDownLatch latch;
        
        /**
         * 构造函数
         * @param ipAddress 目标IP地址
         * @param startPort 起始端口
         * @param endPort 终止端口
         * @param latch 计数器锁
         */
        public PortScanThread(String ipAddress, int startPort, int endPort, CountDownLatch latch) {
            this.ipAddress = ipAddress;
            this.startPort = startPort;
            this.endPort = endPort;
            this.latch = latch;
        }
        
        /**
         * 线程执行方法 - 扫描分配的端口范围
         */
        @Override
        public void run() {
            try {
                for (int port = startPort; port <= endPort; port++) {
                    try (Socket socket = new Socket()) {
                        // 尝试连接目标端口，设置超时时间
                        socket.connect(new InetSocketAddress(ipAddress, port), TIMEOUT);
                        
                        // 连接成功，端口开放
                        String message = "Host: " + ipAddress + " - Port " + port + " is open!";
                        System.out.println(message);
                    } catch (IOException e) {
                        // 连接失败，端口关闭或不可达
                        if (!onlyShowOpenPorts) {
                            System.out.println("Host: " + ipAddress + " - Port " + port + " is closed");
                        }
                    }
                }
            } finally {
                // 任务完成，减少计数器
                latch.countDown();
            }
        }
    }
}    
