package network.grape.app;

import android.app.Activity;
import android.os.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MainActivity of the app.
 */
public class MainActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // SLF4J
    Logger logger = LoggerFactory.getLogger(MainActivity.class);
    logger.info("hello world");
  }
}
