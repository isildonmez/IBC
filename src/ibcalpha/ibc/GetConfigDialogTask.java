// This file is part of IBC.
// Copyright (C) 2004 Steven M. Kearns (skearns23@yahoo.com )
// Copyright (C) 2004 - 2018 Richard L King (rlking@aultan.com)
// For conditions of distribution and use, see copyright notice in COPYING.txt

// IBC is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// IBC is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with IBC.  If not, see <http://www.gnu.org/licenses/>.

package ibcalpha.ibc;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.JDialog;
import javax.swing.JFrame;

class GetConfigDialogTask implements Callable<JDialog>{
    private volatile JDialog mConfigDialog;
    private volatile boolean mGatewayInitialised;
    private final Lock lock = new ReentrantLock();
    private final Condition gotConfigDialog = lock.newCondition();
    private final Condition gatewayInitialised = lock.newCondition();

    @Override
    public JDialog call() throws IbcException, InterruptedException {
        final JFrame mainForm = MainWindowManager.mainWindowManager().getMainWindow();

        /*
            * For the gateway, the main form is loaded right at the start, and long before
            * the menu items become responsive: any attempt to access the Configure > Settings
            * menu item (even after it has been enabled) results in an exception being logged
            * by TWS. 
            * 
            * It's not obvious how long we need to wait before the menu becomes responsive. However the splash
            * frame that appears in front of the gateway main window during initialisation disappears when everything
            * is ready, and its close can be detected as a frame entitled 'Starting application...' and a Closed event.
            * 
            * So we wait for the handler for that frame to call setSplashScreenClosed().
            * 
            */

        lock.lock();
        try {
            while (!mGatewayInitialised) {
                gatewayInitialised.await();
            }
        } finally {
            lock.unlock();
        }

        Utils.logToConsole("Invoking config dialog menu");
        if (!Utils.invokeMenuItem(mainForm, new String[] {"Configure", "Settings"})) throw new IbcException("'Configure > Settings' menu item");

        lock.lock();
        try {
            while (mConfigDialog == null) {
                gotConfigDialog.await();
            }
        } finally {
            lock.unlock();
        }
        return mConfigDialog;
    }  

    void setConfigDialog(JDialog configDialog) {
        lock.lock();
        try {
            mConfigDialog = configDialog;
            gotConfigDialog.signal();
        } finally {
            lock.unlock();
        }
    }

    void setSplashScreenClosed() {
        lock.lock();
        try {
            mGatewayInitialised = true;
            gatewayInitialised.signal();
        } finally {
            lock.unlock();
        }
    }
}
