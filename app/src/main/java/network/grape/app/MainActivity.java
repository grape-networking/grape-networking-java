package network.grape.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SLF4J
        Logger LOG = LoggerFactory.getLogger(MainActivity.class);
        LOG.info("hello world");
    }
}
