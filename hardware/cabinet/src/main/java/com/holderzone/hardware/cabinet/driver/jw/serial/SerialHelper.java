package com.holderzone.hardware.cabinet.util;

/**
 * @Author 李天才
 * @Date: 2026/1/20 9:01
 * @Description:
 */

import android_serialport_api.SerialPort;

import com.bjw.bean.ComBean;
import com.bjw.utils.FuncUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;

public abstract class SerialHelper {
    private SerialPort mSerialPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private String sPort;
    private int iBaudRate;
    private boolean _isOpen;

    public SerialHelper(String sPort, int iBaudRate) {
        this.sPort = "/dev/ttyS3";
        this.iBaudRate = 9600;
        this._isOpen = false;
        this.sPort = sPort;
        this.iBaudRate = iBaudRate;
    }

    public SerialHelper() {
        this("/dev/ttyS3", 9600);
    }

    public SerialHelper(String sPort) {
        this(sPort, 9600);
    }

    public SerialHelper(String sPort, String sBaudRate) {
        this(sPort, Integer.parseInt(sBaudRate));
    }

    public void open() throws SecurityException, IOException, InvalidParameterException {
        this.mSerialPort = new SerialPort(new File(this.sPort), this.iBaudRate, 0);
        this.mOutputStream = this.mSerialPort.getOutputStream();
        this.mInputStream = this.mSerialPort.getInputStream();
        this.mReadThread = new ReadThread();
        this.mReadThread.start();
        this._isOpen = true;
    }

    public void close() {
        if (this.mReadThread != null) {
            this.mReadThread.interrupt();
        }


        if (this.mSerialPort != null) {
            this.mSerialPort.close();
            this.mSerialPort = null;
        }

        if (this.mOutputStream != null) {
            try {
                this.mOutputStream.close();
            } catch (IOException ignored) {
            }
        }

        if (this.mInputStream != null) {
            try {
                this.mInputStream.close();
            } catch (IOException ignored) {
            }
        }


        this._isOpen = false;
    }

    public void send(byte[] bOutArray) {
        try {
            this.mOutputStream.write(bOutArray);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void sendHex(String sHex) {
        byte[] bOutArray = FuncUtil.HexToByteArr(sHex);
        this.send(bOutArray);
    }

    public void sendTxt(String sTxt) {
        byte[] bOutArray = sTxt.getBytes();
        this.send(bOutArray);
    }

    public int getBaudRate() {
        return this.iBaudRate;
    }

    public boolean setBaudRate(int iBaud) {
        if (this._isOpen) {
            return false;
        } else {
            this.iBaudRate = iBaud;
            return true;
        }
    }

    public boolean setBaudRate(String sBaud) {
        int iBaud = Integer.parseInt(sBaud);
        return this.setBaudRate(iBaud);
    }

    public String getPort() {
        return this.sPort;
    }

    public boolean setPort(String sPort) {
        if (this._isOpen) {
            return false;
        } else {
            this.sPort = sPort;
            return true;
        }
    }

    public boolean isOpen() {
        return this._isOpen;
    }

    protected abstract void onDataReceived(ComBean var1);

    private class ReadThread extends Thread {
        private ReadThread() {}

        public void run() {
            super.run();
            while (!this.isInterrupted()) {
                try {
                    if (SerialHelper.this.mInputStream == null) {
                        return;
                    }

                    byte[] buffer = new byte[512];
                    int size = SerialHelper.this.mInputStream.read(buffer);
                    if (size > 0) {
                        ComBean ComRecData = new ComBean(SerialHelper.this.sPort, buffer, size);
                        SerialHelper.this.onDataReceived(ComRecData);
                    }
                } catch (Throwable ignored) {
                    return;
                }
            }

        }
    }

}
