package com.wiqer.proxy.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author Administrator
 */
public class SocketUtils {
    public static boolean addressExist(String ip,int port){
        Socket connect = new Socket();
        try {
            //建立连接
            connect.connect(new InetSocketAddress(ip, port),3000);
            //通过现有方法查看连通状态
            connect.isConnected();
            //为连通
           return  true;
        } catch (IOException e) {
            //当连不通时,直接抛异常,异常捕获即可
            return false;
        }finally{
            try {
                connect.close();
            } catch (IOException e) {
            }
        }
    }
}
