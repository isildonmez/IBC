package ibcalpha.ibc;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;

public abstract class LoginManager {

    private static LoginManager _LoginManager;

    public static void initialise(LoginManager loginManager){
        if (loginManager == null) throw new IllegalArgumentException("loginManager");
        _LoginManager = loginManager;
    }

    public static LoginManager loginManager() {
        return _LoginManager;
    }

    public enum LoginState{
        LOGGED_OUT,
        LOGGED_IN,
        LOGGING_IN,
        TWO_FA_IN_PROGRESS,
        LOGIN_FAILED,
        AWAITING_CREDENTIALS
    }

    private boolean isRestart;
    boolean getIsRestart() {
        return isRestart;
    }
    
    boolean readonlyLoginRequired() {
        boolean readOnly = Settings.settings().getBoolean("ReadOnlyLogin", false);
        if readOnly {
            Utils.logError("Read-only login not supported by Gateway");
            return false;
        }
        return readOnly;
    }
    
    private volatile JFrame loginFrame = null;
    JFrame getLoginFrame() {
        return loginFrame;
    }

    void setLoginFrame(JFrame window) {
        loginFrame = window;
    }
    
    void startSession() {
        // test to see if the -Drestart VM option has been supplied
        isRestart = ! (System.getProperties().getProperty("restart", "").isEmpty());
        int loginDialogDisplayTimeout = Settings.settings().getInt("LoginDialogDisplayTimeout", 60);
        if (isRestart){
            Utils.logToConsole("Re-starting session");
            // TWS/Gateway will re-establish the session with no intervention from IBC needed
        } else {
            Utils.logToConsole("Starting session: will exit if login dialog is not displayed within " + loginDialogDisplayTimeout + " seconds");
            MyScheduledExecutorService.getInstance().schedule(()->{
                GuiExecutor.instance().execute(()->{
                    if (getLoginState() != LoginManager.LoginState.LOGGED_OUT) {
                        // Login diaog has been shown - no need for IBC to exit
                        return;
                    }
                    Utils.exitWithError(ErrorCodes.ERROR_CODE_LOGIN_DIALOG_DISPLAY_TIMEOUT, "IBC closing after TWS/Gateway failed to display login dialog");
                });
            }, loginDialogDisplayTimeout, TimeUnit.SECONDS);
        }
    }

    private volatile LoginState loginState = LoginState.LOGGED_OUT;
    public LoginState getLoginState() {
        return loginState;
    }

    public void setLoginState(LoginState state) {
        if (state == loginState) return;
        loginState = state;
        if (null != loginState) switch (loginState) {
            case TWO_FA_IN_PROGRESS:
                Utils.logToConsole("Second Factor Authentication initiated");
                if (LoginStartTime == null) LoginStartTime = Instant.now();
                break;
            case LOGGING_IN:
                if (LoginStartTime == null) LoginStartTime = Instant.now();
                break;
            case LOGGED_IN:
                Utils.logToConsole("Login has completed");
                loginFrame.setVisible(false);
                if (shutdownAfterTimeTask != null) {
                    shutdownAfterTimeTask.cancel(false);
                    shutdownAfterTimeTask = null;
                }   break;
            default:
                break;
        }
    }

    private Instant LoginStartTime;
    private ScheduledFuture<?> shutdownAfterTimeTask;

    void secondFactorAuthenticationDialogClosed() {
        if (LoginStartTime == null) {
            // login did not proceed from the SecondFactorAuthentication dialog - for
            // example because no second factor device could be selected
            return;
        }
        
        // Second factor authentication dialog timeout period
        final int SecondFactorAuthenticationTimeout = Settings.settings().getInt("SecondFactorAuthenticationTimeout", 180);

        // time (seconds) to allow for login to complete before exiting
        final int exitInterval = Settings.settings().getInt("SecondFactorAuthenticationExitInterval", 40);

        final Duration d = Duration.between(LoginStartTime, Instant.now());
        LoginStartTime = null;
        
        Utils.logToConsole("Duration since login: " + d.getSeconds() + " seconds");

        if (d.getSeconds() < SecondFactorAuthenticationTimeout) {
            // The 2FA prompt must have been handled by the user, so authentication
            // should be under way
            Utils.logToConsole("If login has not completed, IBC will exit in " + exitInterval + " seconds");
            restartAfterTime(exitInterval, "IBC closing because login has not completed after Second Factor Authentication");
            return;
        }
        
        if (!reloginRequired()) {
            Utils.logToConsole("Re-login after second factor authentication timeout not required");
            return;
        }
        
        // The 2FA prompt hasn't been handled by the user, so we re-initiate the login
        // sequence after a short delay
        Utils.logToConsole("Re-login after second factor authentication timeout in 5 second");
        MyScheduledExecutorService.getInstance().schedule(() -> {
            GuiDeferredExecutor.instance().execute(
                () -> {getLoginHandler().initiateLogin(getLoginFrame());}
            );
        }, 5, TimeUnit.SECONDS);
    }
    
    private boolean reloginRequired() {
        if (Settings.settings().getString("ReloginAfterSecondFactorAuthenticationTimeout", "").isEmpty()) {
            if (!Settings.settings().getString("ExitAfterSecondFactorAuthenticationTimeout", "").isEmpty()) {
                return Settings.settings().getBoolean("ExitAfterSecondFactorAuthenticationTimeout", false);
            }
            return false;
        }
        return Settings.settings().getBoolean("ReloginAfterSecondFactorAuthenticationTimeout", false);
    }
    
    void restartAfterTime(final int secondsTillShutdown, final String message) {
        try {
            shutdownAfterTimeTask = MyScheduledExecutorService.getInstance().schedule(()->{
                GuiExecutor.instance().execute(()->{
                    if (getLoginState() == LoginManager.LoginState.LOGGED_IN) {
                        Utils.logToConsole("Login has already completed - no need for IBC to exit");
                        return;
                    }
                    Utils.exitWithError(ErrorCodes.ERROR_CODE_2FA_LOGIN_TIMED_OUT, message);
                });
            }, secondsTillShutdown, TimeUnit.SECONDS);
        } catch (Throwable e) {
            Utils.exitWithException(99999, e);
        }
    }

    public abstract void logDiagnosticMessage();

    public abstract String IBAPIPassword();

    public abstract String IBAPIUserName();

    public abstract AbstractLoginHandler getLoginHandler();

    public abstract void setLoginHandler(AbstractLoginHandler handler);

}
