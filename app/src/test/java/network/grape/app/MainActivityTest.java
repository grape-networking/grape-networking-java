package network.grape.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.os.Build;
import java.net.SocketException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Test the MainActivity.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.JELLY_BEAN_MR2})
public class MainActivityTest {

  // not being included in jacoco coverage - i think its because we're using junit4 + roboelec
  @Test
  public void startVpnTest() throws Exception {
    ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class);
    MainActivity mainActivity = spy(ac.get());

    doReturn(true).when(mainActivity).checkForActiveInterface(any());
    mainActivity.startVpn();

    when(mainActivity.checkForActiveInterface(any())).thenThrow(SocketException.class);

    mainActivity.startVpn();
  }
}
