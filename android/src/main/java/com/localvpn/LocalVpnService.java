package com.localvpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalVpnService extends VpnService {
    private static final String TAG = "ZaferLocalVpnService";
    private ParcelFileDescriptor vpnInterface = null;
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    public static final String BROADCAST_VPN_STATE = "com.dnsmanager.VPN_STATE";
    private static boolean isRunning = false;

    private PendingIntent pendingIntent;

    private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;

    private Selector udpSelector;
    private Selector tcpSelector;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VPN Service Created");
    }

    public void startBuffer(){
        try
        {
            if (vpnInterface != null){
                udpSelector = Selector.open();
                tcpSelector = Selector.open();
                deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
                deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
                networkToDeviceQueue = new ConcurrentLinkedQueue<>();

                executorService = Executors.newFixedThreadPool(5);
                executorService.submit(new UDPInput(networkToDeviceQueue, udpSelector));
                executorService.submit(new UDPOutput(deviceToNetworkUDPQueue, udpSelector, this));
                executorService.submit(new TCPInput(networkToDeviceQueue, tcpSelector));
                executorService.submit(new TCPOutput(deviceToNetworkTCPQueue, networkToDeviceQueue, tcpSelector, this));
                executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                        deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue));
                registerReceiver(stopReceiver, new IntentFilter(Constants.ACTION_FOREGROUND_SERVICE_STOP));
                sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
                Log.i(TAG, "Started");
            }
        }
        catch (IOException e)
        {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            Log.e(TAG, "Error starting service", e);
            cleanup();
        }
    }
    public static boolean isRunning()
    {
        return isRunning;
    }

    private void cleanup()
    {
        deviceToNetworkTCPQueue = null;
        deviceToNetworkUDPQueue = null;
        networkToDeviceQueue = null;
        ByteBufferPool.clear();
        closeResources(udpSelector, tcpSelector, vpnInterface);
    }

    private static void closeResources(Closeable... resources)
    {
        for (Closeable resource : resources)
        {
            try
            {
                resource.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (vpnInterface != null) {
            // VPN is already established
            return START_NOT_STICKY;
        }
            Log.e(TAG, "VPN Gelen aksiyon-1");
        String action = intent.getAction();
        if (action != null) {
            Log.e(TAG, "VPN Gelen aksiyon"+ action);
            if (action.equals(Constants.ACTION_FOREGROUND_SERVICE_STOP)) {
                stopSelf();
            }
            
        }
        Builder builder = new Builder();
        builder.setSession("MyVPN")
                .addAddress(VPN_ADDRESS, 24)
                .addDnsServer("185.218.124.25")
                .addRoute(VPN_ROUTE, 0);

        try {
            vpnInterface = builder.establish();
            startBuffer();
            Log.d(TAG, "VPN Established");
        } catch (Exception e) {
            Log.e(TAG, "Error establishing VPN", e);
        }

        return START_STICKY;
    }

   @Override
    public void onDestroy() {
        Log.d(TAG, "VPN Interface ondesteroy");
        super.onDestroy();
        stopVpn();
        Log.d(TAG, "VPN Service Destroyed");
    }

    private void stopVpn()
	{

		if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
                Log.d(TAG, "VPN Interface Closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }

		sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", false));
	}

    private BroadcastReceiver stopReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
            Log.e(TAG, "OnReceive Brodcast receiver");
			if (intent == null || intent.getAction() == null)
			{
				return;
			}

			if (Constants.ACTION_FOREGROUND_SERVICE_STOP.equals(intent.getAction()))
			{
				stopVpn();

			}
		}
	};

    

    private static class VPNRunnable implements Runnable
    {
        private FileDescriptor vpnFileDescriptor;

        private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
        private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

        public VPNRunnable(FileDescriptor vpnFileDescriptor,
                           ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue,
                           ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue,
                           ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue)
        {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run()
        {
            Log.i(TAG, "Runnable Started");

            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();

            try
            {
                ByteBuffer bufferToNetwork = null;
                boolean dataSent = true;
                boolean dataReceived;
                while (!Thread.interrupted())
                {
                    if (dataSent)
                        bufferToNetwork = ByteBufferPool.acquire();
                    else
                        bufferToNetwork.clear();

                    // TODO: Block when not connected
                    int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0)
                    {
                        dataSent = true;
                        bufferToNetwork.flip();
                        Packet packet = new Packet(bufferToNetwork);
                        if (packet.isUDP())
                        {
                            deviceToNetworkUDPQueue.offer(packet);
                        }
                        else if (packet.isTCP())
                        {
                            deviceToNetworkTCPQueue.offer(packet);
                        }
                        else
                        {
                            Log.w(TAG, "Unknown packet type");
                            Log.w(TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }
                    }
                    else
                    {
                        dataSent = false;
                    }

                    ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    if (bufferFromNetwork != null)
                    {
                        bufferFromNetwork.flip();
                        while (bufferFromNetwork.hasRemaining())
                            vpnOutput.write(bufferFromNetwork);
                        dataReceived = true;

                        ByteBufferPool.release(bufferFromNetwork);
                    }
                    else
                    {
                        dataReceived = false;
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataSent && !dataReceived)
                        Thread.sleep(10);
                }
            }
            catch (InterruptedException e)
            {
                Log.i(TAG, "Stopping");
            }
            catch (IOException e)
            {
                Log.w(TAG, e.toString(), e);
            }
            finally
            {
                closeResources(vpnInput, vpnOutput);
            }
        }
    }

}