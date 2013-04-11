package com.aviary.glimageview;

import java.io.IOException;
import java.io.InputStream;


import com.aviary.example.glimageview.R;
import com.aviary.glimageview.GLImageViewRenderer.OnBitmapReadOutCompleted;

import android.os.Bundle;
import android.os.Handler;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.view.Menu;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

public class MainActivity extends Activity {
	GLImageView mGLImageView;
	Bitmap mCurrentBitmap;
	Handler mHandler = new Handler();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		
		loadDefaultImage();
		
		SimpleGLImageProgram program = new SimpleGLImageProgram();
		
		mGLImageView = new GLImageView(this, program);
		ViewGroup group = (ViewGroup) findViewById(R.id.group );
		group.addView(mGLImageView);
		
		mGLImageView.setImage(mCurrentBitmap);
	
		mGLImageView.requestRender();
		
		mHandler.postDelayed(new Runnable(){
			@Override
			public void run() {
				getBitmap();
			}
		}, 1000);
	}
	
	// Loading
	void loadDefaultImage() {		
		try {
			Options options = new Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		
			InputStream io = getAssets().open( "test.jpg" );
//			InputStream io = getAssets().open( "test_small.jpg" );
		
			Bitmap bitmap = BitmapFactory.decodeStream( io, null, options );
			mCurrentBitmap = bitmap;
			io.close();
		} catch ( IOException e ) {}
	}
	
	void getBitmap(){
		OnBitmapReadOutCompleted listener = new OnBitmapReadOutCompleted() {
			@Override
			public void onBitmapReadOutCompleted(Bitmap bitmap) {
				runOnUiThread(new Runnable(){
					@Override
					public void run() {
						Toast.makeText(getBaseContext(), "Image Read Complete!!", Toast.LENGTH_SHORT).show();
					}
				});
			}
		};
		
		mGLImageView.requestBitmap(listener, null);
	}
}
