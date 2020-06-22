package com.xwr.ymodenreader;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Create by xwr on 2020/6/19
 * Describe:
 */
public class SerialPortUtil {
  private String TAG = SerialPortUtil.class.getSimpleName();
  private SerialPort mSerialPort;
  private OutputStream mOutputStream;
  private InputStream mInputStream;
  private ReadThread mReadThread;
  private String path = "/dev/ttyHSL1";
  private int baudrate = 115200;
  private static SerialPortUtil portUtil;
  private OnDataReceiveListener onDataReceiveListener = null;
  private boolean isStop = false;
  public boolean serialPortStatus = false; //是否打开串口标志

  public interface OnDataReceiveListener {
    public void onDataReceive(byte[] buffer, int size);
  }

  public void setOnDataReceiveListener(
    OnDataReceiveListener dataReceiveListener) {
    onDataReceiveListener = dataReceiveListener;
  }

  public static SerialPortUtil getInstance() {
    if (null == portUtil) {
      portUtil = new SerialPortUtil();
    }
    return portUtil;
  }

  /**
   * 初始化串口信息
   */
  public void onCreate() {
    if (!serialPortStatus) {
      try {
        mSerialPort = new SerialPort(new File(path), baudrate);
        mOutputStream = mSerialPort.getOutputStream();
        mInputStream = mSerialPort.getInputStream();
        mReadThread = new ReadThread();
        serialPortStatus = true;
        isStop = false;
        mReadThread.start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * 发送指令到串口
   *
   * @param data
   * @return
   */
  public boolean sendCmds(byte[] data) {
    boolean result = true;
    try {
      if (mOutputStream != null) {
        mOutputStream.write(data);
      } else {
        result = false;
      }
    } catch (IOException e) {
      e.printStackTrace();
      result = false;
    }
    return result;
  }

  public boolean sendBuffer(byte[] mBuffer) {
    boolean result = true;
    String tail = "\r\n";
    byte[] tailBuffer = tail.getBytes();
    byte[] mBufferTemp = new byte[mBuffer.length + tailBuffer.length];
    System.arraycopy(mBuffer, 0, mBufferTemp, 0, mBuffer.length);
    System.arraycopy(tailBuffer, 0, mBufferTemp, mBuffer.length, tailBuffer.length);
    //注意：我得项目中需要在每次发送后面加\r\n，大家根据项目项目做修改，也可以去掉，直接发送mBuffer
    try {
      if (mOutputStream != null) {
        mOutputStream.write(mBufferTemp);
      } else {
        result = false;
      }
    } catch (IOException e) {
      e.printStackTrace();
      result = false;
    }
    return result;
  }

  private class ReadThread extends Thread {
    @Override
    public void run() {
      super.run();
      while (!isStop && !isInterrupted()) {
        int size;
        try {
          if (mInputStream == null)
            return;
          byte[] buffer = new byte[1024 * 2];
          //是否有数据
          if (mInputStream.available() != 0) {
            //receiver data
            size = mInputStream.read(buffer);
            byte[] b = Arrays.copyOf(buffer, size);  //存放实际读取的数据内容
            if (size > 0) {
              Log.d(TAG, "length is:" + size + ",data is:" + bytes2HexString(buffer, size));
              if (null != onDataReceiveListener) {
                onDataReceiveListener.onDataReceive(b, size);
              }
            }
          }
          Thread.sleep(10);
        } catch (Exception e) {
          e.printStackTrace();
          return;
        }
      }
    }
  }

  public boolean isOpen() {
    return serialPortStatus;
  }

  /**
   * 关闭串口
   */
  public void closeSerialPort() {
    isStop = true;
    if (mReadThread != null) {
      mReadThread.interrupt();
    }
    try {
      if (mInputStream != null) {
        mInputStream.close();
      }
      if (mOutputStream != null) {
        mOutputStream.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (mSerialPort != null) {
      mSerialPort.close();
      serialPortStatus = false;
    }
  }

  public static String bytes2HexString(byte[] b, int length) {
    StringBuffer result = new StringBuffer();
    String hex;
    for (int i = 0; i < length; i++) {
      hex = Integer.toHexString(b[i] & 0xFF);
      if (hex.length() == 1) {
        hex = '0' + hex;
      }
      result.append(hex.toUpperCase()).append(" ");
    }
    return result.toString();
  }
}
