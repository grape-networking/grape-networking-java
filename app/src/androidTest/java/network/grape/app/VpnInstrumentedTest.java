package network.grape.app;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.RequiresDevice;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import network.grape.service.GrapeVpnService;
import org.junit.Rule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * This test will start up the VPN service, and attempt to use the connection for some things.
 */
public class VpnInstrumentedTest {

  @Rule
  public GrantPermissionRule internetPermission =
      GrantPermissionRule.grant(Manifest.permission.INTERNET);

  @Rule
  public GrantPermissionRule networkStatePermission =
      GrantPermissionRule.grant(Manifest.permission.ACCESS_NETWORK_STATE);

  //un-disable this test once the VPN accepts traffic correctly
  // @Disabled
  @RequiresDevice
  @Test
  public void testVpnConnection()
      throws InterruptedException, ExecutionException, TimeoutException {

    //start the service up
    Context context = getInstrumentation().getTargetContext();
    Intent serviceIntent = new Intent(context, GrapeVpnService.class);
    ComponentName componentName = context.startService(serviceIntent);

    assertNotNull(componentName);
    assertEquals(componentName.getPackageName(), context.getPackageName());

    RequestQueue requestQueue = Volley.newRequestQueue(context);
    String url = "https://www.google.com";
    RequestFuture<String> future = RequestFuture.newFuture();
    StringRequest request = new StringRequest(url, future, future);
    requestQueue.add(request);
    String response = future.get(5, TimeUnit.SECONDS);
    System.out.println("Got " + response.length() + " bytes from google");

    //shut the service down
    context.stopService(serviceIntent);
  }

  @RequiresDevice
  @Test
  void startFromActivityTest() throws InterruptedException {
    Context context = getInstrumentation().getTargetContext();
    Intent activityIntent = new Intent(context, MainActivity.class);
    activityIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
    ApplicationProvider.getApplicationContext().startActivity(activityIntent);
    Thread.sleep(1000);
    allowPermissionsIfNeeded();
    Thread.sleep(1000);

    // TODO:(jason) pause, resume, destory activity to see how the lifecycle of the activity affects
    // the way the service starts / stops
  }

  private static void allowPermissionsIfNeeded() {
    if (Build.VERSION.SDK_INT >= 23) {
      System.out.println("High enough SDK to try to request permissions");
      UiDevice device = UiDevice.getInstance(getInstrumentation());
      UiObject allowPermissions = device.findObject(new UiSelector().text("OK"));
      if (allowPermissions.exists()) {
        try {
          System.out.println("Trying to accept the permission");
          allowPermissions.click();
        } catch (UiObjectNotFoundException e) {
          System.out.println("There is no permissions dialog to interact with");
        }
      } else {
        System.out.println("Can't find the allow permissions object");
      }
    } else {
      System.out.println("API is too old, can't get permissions");
    }
  }
}
