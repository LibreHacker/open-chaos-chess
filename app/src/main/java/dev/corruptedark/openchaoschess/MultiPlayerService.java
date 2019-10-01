package dev.corruptedark.openchaoschess;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MultiPlayerService {

    private interface Constants {
        public static final int READ = 0;
        public static final int WRITE = 1;
        public static final int ERROR = 2;
        public static final String ERROR_KEY = "error";
        public static final String SEND_FAILED = "send_failed";
    }

    private final String TAG = "Multi Player Service";
    private final ServiceHandler handler;
    private ConnectedThread connectedThread;

    public MultiPlayerService(BluetoothSocket connectedSocket) {
        handler = new ServiceHandler();
        connectedThread = new ConnectedThread(connectedSocket);
        connectedThread.start();
    }

    public synchronized void sendData(String data)
    {
        byte[] bytes = data.getBytes();

        connectedThread.write(bytes);
    }

    public synchronized String getMostRecentData()
    {
        return handler.getLastReceived();
    }

    public synchronized boolean hasNewMessage()
    {
        return handler.hasNewMessage();
    }

    public synchronized boolean hasNewError()
    {
        return handler.hasNewError();
    }

    static class ServiceHandler extends Handler {
        private String lastSent = null;
        private String lastReceived = null;
        private String lastError = null;
        private boolean newMessage = false;
        private boolean newError = false;

        public String getLastSent() {
            return lastSent;
        }

        public String getLastReceived() {
            newMessage = false;
            return lastReceived;
        }

        public String getLastError() {
            newError = false;
            return lastError;
        }

        public boolean hasNewMessage() {
            return newMessage;
        }

        public boolean hasNewError() {
            return newError;
        }

        @Override
        public void handleMessage(Message message) {

            switch (message.what) {
                case Constants.READ:
                    byte[] readBuffer = (byte[]) message.obj;

                    newMessage = true;
                    lastReceived = new String(readBuffer, 0, message.arg1);
                    break;
                case Constants.WRITE:
                    byte[] writeBuffer = (byte[]) message.obj;

                    if(writeBuffer != null)
                        lastSent = new String(writeBuffer);
                    break;
                case Constants.ERROR:
                    newError = true;
                    lastError = message.getData().getString(Constants.ERROR_KEY);
                    break;

            }

        }
    }

    private class ConnectedThread extends Thread {
        private final int BUFFER_SIZE = 1024;
        private final BluetoothSocket connectedSocket;
        private final InputStream inStream;
        private final OutputStream outStream;
        private volatile byte[] inBuffer;
        private volatile byte[] outBuffer;

        public ConnectedThread(BluetoothSocket socket) {
            connectedSocket = socket;
            InputStream tempInput = null;
            OutputStream tempOut = null;

            try {
                tempInput = socket.getInputStream();
            }
            catch (IOException e)
            {
                Log.e(TAG,"Failed to create input stream",e);
            }

            try {
                tempOut = socket.getOutputStream();
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to create output stream",e);
            }

            inStream = tempInput;
            outStream = tempOut;
        }

        public void run() {
            Looper.prepare();
            inBuffer = new byte[BUFFER_SIZE];
            //outBuffer = new byte[BUFFER_SIZE];

            int inBytesReturned;
            //int outBytesReturned;

            while (true) {
                try {
                    inBytesReturned = inStream.read(inBuffer);

                    Message inMessage = handler.obtainMessage(Constants.READ, inBytesReturned, -1, inBuffer);
                    inMessage.sendToTarget();
                    handler.handleMessage(inMessage);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Input stream disconnected", e);
                    //cancel();
                    break;
                }
            }
        }

        public synchronized void write(byte[] bytes) {
            try {

                outStream.write(bytes);

                Message outMessage = handler.obtainMessage(Constants.WRITE, -1, -1, bytes);
                outMessage.sendToTarget();
                handler.handleMessage(outMessage);
            }
            catch (IOException e) {
                Log.e(TAG, "Sending data failed", e);

                Message errorMessage = handler.obtainMessage(Constants.ERROR);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.ERROR_KEY, Constants.SEND_FAILED);
                errorMessage.setData(bundle);
                handler.sendMessage(errorMessage);
            }
        }

        public void cancel() {
            try {
                connectedSocket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }


    }

}
