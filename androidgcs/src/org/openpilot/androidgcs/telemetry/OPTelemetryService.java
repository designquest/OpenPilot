/**
 ******************************************************************************
 * @file       OPTelemetryService.java
 * @author     The OpenPilot Team, http://www.openpilot.org Copyright (C) 2012.
 * @brief      Provides UAVTalk telemetry over multiple physical links.  The
 *             details of each of these are in their respective connection
 *             classes.  This mostly creates those threads based on the selected
 *             preferences.
 * @see        The GNU Public License (GPL) Version 3
 *
 *****************************************************************************/
/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.openpilot.androidgcs.telemetry;

import java.lang.ref.WeakReference;

import org.openpilot.uavtalk.UAVDataObject;
import org.openpilot.uavtalk.UAVObjectManager;
import org.openpilot.uavtalk.uavobjects.UAVObjectsInitialize;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class OPTelemetryService extends Service {

	// Logging settings
	private final String TAG = OPTelemetryService.class.getSimpleName();
	public static int LOGLEVEL = 2;
	public static boolean DEBUG = LOGLEVEL > 1;
	public static boolean WARN = LOGLEVEL > 0;

	// Intent category
	public final static String INTENT_CATEGORY_GCS        = "org.openpilot.intent.category.GCS";

	// Intent actions
	public final static String INTENT_ACTION_CONNECTED    = "org.openpilot.intent.action.CONNECTED";
	public final static String INTENT_ACTION_DISCONNECTED = "org.openpilot.intent.action.DISCONNECTED";

	// Variables for local message handler thread
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private static HandlerThread thread;

	// Message ids
	static final int MSG_START        = 0;
	static final int MSG_CONNECT      = 1;
	static final int MSG_DISCONNECT   = 3;
	static final int MSG_TOAST        = 100;

	private boolean terminate = false;

	private Thread activeTelem;
	private TelemetryTask telemTask;

	private final IBinder mBinder = new LocalBinder();

	static class ServiceHandler extends Handler {
	    private final WeakReference<OPTelemetryService> mService;

	    ServiceHandler(OPTelemetryService service, Looper looper) {
	    	super(looper);
	        mService = new WeakReference<OPTelemetryService>(service);
	    }

	    @Override
	    public void handleMessage(Message msg)
	    {
	    	OPTelemetryService service = mService.get();
	         if (service != null) {
	              service.handleMessage(msg);
	         }
	    }
	}

	void handleMessage(Message msg) {
		switch(msg.arg1) {
		case MSG_START:
			stopSelf(msg.arg2);
			break;
		case MSG_CONNECT:
			terminate = false;
			int connection_type;
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(OPTelemetryService.this);
			try {
				connection_type = Integer.decode(prefs.getString("connection_type", ""));
			} catch (NumberFormatException e) {
				connection_type = 0;
			}

			switch(connection_type) {
			case 0: // No connection
				return;
			case 2:
				Toast.makeText(getApplicationContext(), "Attempting BT connection", Toast.LENGTH_SHORT).show();
				telemTask = new BluetoothUAVTalk(this);
				activeTelem = new Thread(telemTask, "Bluetooth telemetry thread");
				break;
			case 3:
				Toast.makeText(getApplicationContext(), "Attempting TCP connection", Toast.LENGTH_SHORT).show();
				telemTask = new TcpUAVTalk(this);
				activeTelem = new Thread(telemTask, "Tcp telemetry thread");
				break;
			case 4:
				Toast.makeText(getApplicationContext(), "Attempting USB HID connection", Toast.LENGTH_SHORT).show();
				telemTask = new HidUAVTalk(this);
				activeTelem = new Thread(telemTask, "Hid telemetry thread");
				break;
			default:
				throw new Error("Unsupported");
			}
			activeTelem.start();
			break;
		case MSG_DISCONNECT:
			Toast.makeText(getApplicationContext(), "Disconnect requested", Toast.LENGTH_SHORT).show();
			if (DEBUG) Log.d(TAG, "Calling disconnect");
			terminate = true;
			if (telemTask != null) {
				telemTask.disconnect();
				telemTask = null;

				try {
					activeTelem.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			else  if (activeTelem != null) {
				activeTelem.interrupt();
				try {
					activeTelem.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				activeTelem = null;
			}
			if (DEBUG) Log.d(TAG, "Telemetry thread terminated");
			Intent intent = new Intent();
			intent.setAction(INTENT_ACTION_DISCONNECTED);
			sendBroadcast(intent,null);

			stopSelf();

			break;
		case MSG_TOAST:
			Toast.makeText(this, (String) msg.obj, Toast.LENGTH_SHORT).show();
			break;
		default:
			System.out.println(msg.toString());
			throw new Error("Invalid message");
		}
	}

	/**
	 * Called when the service starts.  It creates a thread to handle messages (e.g. connect and disconnect)
	 * and based on the stored preference will send itself a connect signal if needed.
	 */
	public void startup() {
		Toast.makeText(getApplicationContext(), "Telemetry service starting", Toast.LENGTH_SHORT).show();

		thread = new HandlerThread("TelemetryServiceHandler", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(this, mServiceLooper);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(OPTelemetryService.this);
		if(prefs.getBoolean("autoconnect", false)) {
			Message msg = mServiceHandler.obtainMessage();
			msg.arg1 = MSG_CONNECT;
			msg.arg2 = 0;
			mServiceHandler.sendMessage(msg);
		}

	}

	@Override
	public void onCreate() {
		if (DEBUG)
			Log.d(TAG, "Telemetry service created");
		startup();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Currently only using as bound service

		// If we get killed, after returning from here, restart
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onDestroy() {

		if (telemTask != null) {
			Log.d(TAG, "onDestroy() shutting down telemetry task");
			telemTask.disconnect();
			telemTask = null;

			try {
				activeTelem.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Log.d(TAG, "onDestory() shut down telemetry task");
		Toast.makeText(this, "Telemetry service done", Toast.LENGTH_SHORT).show();
	}

	public class LocalBinder extends Binder {
		public TelemTask getTelemTask(int id) {
			if (telemTask != null)
				return telemTask.getTelemTaskIface();
			return null;
		}
		public void openConnection() {
			Toast.makeText(getApplicationContext(), "Requested open connection", Toast.LENGTH_SHORT).show();
			Message msg = mServiceHandler.obtainMessage();
			msg.arg1 = MSG_CONNECT;
			mServiceHandler.sendMessage(msg);
		}
		public void stopConnection() {
			Message msg = mServiceHandler.obtainMessage();
			msg.arg1 = MSG_DISCONNECT;
			mServiceHandler.sendMessage(msg);
		}
		public boolean isConnected() {
			return (activeTelem != null) && (telemTask != null) && (telemTask.getConnected());
		}
	};

	public void toastMessage(String msgText) {
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = MSG_TOAST;
		msg.obj = msgText;
		mServiceHandler.sendMessage(msg);
	}

	/**
	 * This is used by other processes to get a handle to the object manager
	 */
	public interface TelemTask {
		public UAVObjectManager getObjectManager();
	};
}
