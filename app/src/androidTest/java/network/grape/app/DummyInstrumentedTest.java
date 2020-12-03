package network.grape.app;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.jupiter.api.Test;

/**
 * A Dummy InstrumentedTest just to make sure it works.
 */
public class DummyInstrumentedTest {
  @Test
  public void useAppContext() {
    // Context of the app under test.
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertEquals("network.grape.app", appContext.getPackageName());
  }
}
