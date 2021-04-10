package network.grape.tcp_client;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.nguyenhoanglam.imagepicker.model.Config;
import com.nguyenhoanglam.imagepicker.model.Image;
import com.nguyenhoanglam.imagepicker.ui.imagepicker.GlideLoader;
import com.nguyenhoanglam.imagepicker.ui.imagepicker.ImagePicker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImagePicker.with(this)
                .setFolderMode(false)
                .setMultipleMode(false)
                .setShowNumberIndicator(true)
                .setRequestCode(100)
                .start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (ImagePicker.shouldHandleResult(requestCode, resultCode, data, 100)) {
            ArrayList<Image> images = ImagePicker.getImages(data);
            Image image = images.get(0);
            ImageView imageView = (ImageView) findViewById(R.id.imageView);
            imageView.setImageURI(image.getUri());
        }
    }

    // https://stackoverflow.com/questions/25086868/how-to-send-images-through-sockets-in-java
    // https://stackoverflow.com/questions/37779515/how-can-i-convert-an-imageview-to-byte-array-in-android-studio
    public void sendImage(View v) {
        Thread thread = new Thread(() -> {
            InetSocketAddress inetSocketAddress = new InetSocketAddress("10.0.0.111", 8888);
            Socket s = new Socket();
            try {
                s.connect(inetSocketAddress);
                OutputStream outputStream = s.getOutputStream();
                ImageView imageView = (ImageView) findViewById(R.id.imageView);
                Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] imageInByte = baos.toByteArray();
                byte[] size = ByteBuffer.allocate(4).putInt(imageInByte.length).array();
                outputStream.write(size);
                outputStream.write(imageInByte);
                outputStream.flush();

                InputStream inputStream = s.getInputStream();
                byte[] sizeAr = new byte[4];
                inputStream.read(sizeAr);
                int inputSize = ByteBuffer.wrap(sizeAr).asIntBuffer().get();
                byte[] imageAr = new byte[inputSize];
                inputStream.read(imageAr);
                Drawable drawable = BitmapDrawable.createFromStream(inputStream, "Network");
                imageView.setImageDrawable(drawable);

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }
}