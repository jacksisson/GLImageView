package com.aviary.glimageview;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;

public interface GLImagingProgram {
	    
	public void setup();
	
	public void setBitmap(Bitmap bitmap);
	
	public void render(Matrix transformMatrix, RectF imageBoundsRect);

	public void readImage(Bitmap bitmap);
}
