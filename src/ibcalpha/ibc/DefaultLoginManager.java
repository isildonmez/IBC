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

import javax.swing.JFrame;

public class DefaultLoginManager extends LoginManager {

    public DefaultLoginManager(String[] args) {
        fromSettings = true;
        message = "getting username and password from settings";
    }

    private final String message;

    private volatile AbstractLoginHandler loginHandler = null;

    private final boolean fromSettings;

    private volatile String IBAPIUserName;

    private volatile String IBAPIPassword;


    @Override
    public void logDiagnosticMessage(){
        Utils.logToConsole("using default login manager: " + message);
    }

    @Override
    public String IBAPIPassword() {
        String password = Settings.settings().getString("IbPassword", "");
        return password;
    }

    @Override
    public String IBAPIUserName() {
        return Settings.settings().getString("IbLoginId", "");
    }

    @Override
    public JFrame getLoginFrame() {
        return super.getLoginFrame();
    }

    @Override
    public void setLoginFrame(JFrame window) {
        super.setLoginFrame(window);
    }

    @Override
    public AbstractLoginHandler getLoginHandler() {
        return loginHandler;
    }

    @Override
    public void setLoginHandler(AbstractLoginHandler handler) {
        loginHandler = handler;
    }
}
