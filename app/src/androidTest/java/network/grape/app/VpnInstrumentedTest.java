package network.grape.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import network.grape.service.GrapeVpnService;
import org.junit.Rule;
import org.junit.Test;

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

  @Test
  public void testVpnConnection() throws InterruptedException, ExecutionException, TimeoutException {

    //start the service up
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
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
}
