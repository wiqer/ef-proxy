package com.wiqer.proxy.utils;

import java.net.*;
import java.util.Enumeration;

public final class IpUtils {
        /**
         * IP地址的正则表达式.
         */
        public static final String IP_REGEX = "((\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3})";

        private static volatile String cachedIpAddress;

        public static void main(String[] args) throws UnknownHostException {
            System.out.println(getIp());
            System.out.println(getHostName());
            System.out.println(isLocalIp(("10.243.0.4")));
        }

        /**
         * 获取本机IP地址.
         *
         * <p>
         * 有限获取外网IP地址.
         * 也有可能是链接着路由器的最终IP地址.
         * </p>
         *
         * @return 本机IP地址
         */
        public static String getIp() {
            if (null != cachedIpAddress) {
                return cachedIpAddress;
            }
            Enumeration<NetworkInterface> netInterfaces;
            try {
                netInterfaces = NetworkInterface.getNetworkInterfaces();
            } catch (final SocketException ex) {
                return null;
            }
            String localIpAddress = null;
            while (netInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = netInterfaces.nextElement();
                Enumeration<InetAddress> ipAddresses = netInterface.getInetAddresses();
                while (ipAddresses.hasMoreElements()) {
                    InetAddress ipAddress = ipAddresses.nextElement();
                    if (isPublicIpAddress(ipAddress)) {
                        String publicIpAddress = ipAddress.getHostAddress();
                        cachedIpAddress = publicIpAddress;
                        return publicIpAddress;
                    }
                    if (isLocalIpAddress(ipAddress)) {
                        localIpAddress = ipAddress.getHostAddress();
                    }
                }
            }
            cachedIpAddress = localIpAddress;
            return localIpAddress;
        }
    public static boolean isLocalIp(String addr) {
        Enumeration<NetworkInterface> netInterfaces;
        try {
            netInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (final SocketException ex) {
            return false;
        }
        while (netInterfaces.hasMoreElements()) {
            NetworkInterface netInterface = netInterfaces.nextElement();
            Enumeration<InetAddress> ipAddresses = netInterface.getInetAddresses();
            while (ipAddresses.hasMoreElements()) {
                InetAddress ipAddress = ipAddresses.nextElement();
                if (isPublicIpAddress(ipAddress)) {
                    String publicIpAddress = ipAddress.getHostAddress();
                   if( addr.equals(publicIpAddress))
                    return true;
                }
                if (isLocalIpAddress(ipAddress)) {
                    return true;
                }
            }
        }
        return false;
    }
        private static boolean isPublicIpAddress(final InetAddress ipAddress) {
            return !ipAddress.isSiteLocalAddress() && !ipAddress.isLoopbackAddress() && !isV6IpAddress(ipAddress);
        }

        private static boolean isLocalIpAddress(final InetAddress ipAddress) {
            return ipAddress.isSiteLocalAddress() && !ipAddress.isLoopbackAddress() && !isV6IpAddress(ipAddress);
        }

        private static boolean isV6IpAddress(final InetAddress ipAddress) {
            return ipAddress.getHostAddress().contains(":");
        }

        /**
         * 获取本机Host名称.
         *
         * @return 本机Host名称
         */
        public static String getHostName() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (final UnknownHostException ex) {
                return null;
            }
        }
        public static SocketAddress addr2SocketAddress(final String addr) {
            int i = addr.lastIndexOf(":");
            String host = addr.substring(0, i);
            String port = addr.substring(i + 1);
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, Integer.parseInt(port));
            return inetSocketAddress;
        }
    }
