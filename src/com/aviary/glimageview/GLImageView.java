package com.aviary.glimageview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.aviary.glimageview.GLImageViewRenderer.OnBitmapReadOutCompleted;
import com.aviary.glimageview.GLImageViewRenderer.OnImageBitmapLoaded;
import com.aviary.glimageview.GLImageViewRenderer.OnRenderCompletedListener;
import com.aviary.glimageview.easing.Cubic;

public class GLImageView extends GLSurfaceView {

	@SuppressWarnings("unused")
	private final static String LOG_TAG = "GLImageView";
	
	GLImageViewRenderer mCurrentRenderer;

	ScaleGestureDetector mScaleDetector;
	ScaleListener mScaleListener;
	GestureListener mGestureListener;
	GestureDetector mGestureDetector;
	
	Cubic mEasing = new Cubic();
	
	PointF mLastValidCenter;
	
	public GLImageView(Context context){
		this(context, null);
	}
	
	// Constructor
	public GLImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		this.getHolder().setFormat(PixelFormat.TRANSLUCENT);
		this.setZOrderMediaOverlay(true);
		
		// Gestures
		mScaleListener = new ScaleListener();
		mScaleDetector = new ScaleGestureDetector(context, mScaleListener);
		mGestureListener = new GestureListener();
		mGestureDetector = new GestureDetector( getContext(), mGestureListener, null, true );
		mLastValidCenter = new PointF();		
	}
	
	public void setupRendererWithProgram(GLImagingProgram program){
		if(program == null){
			program = new SimpleGLImageProgram();
		}
		
		// OpenGL Setup
		this.setEGLContextClientVersion( 2 );
		this.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
//		this.setPreserveEGLContextOnPause(true);
		mCurrentRenderer = new GLImageViewRenderer(program);
		this.setRenderer( mCurrentRenderer );
		this.setRenderMode(RENDERMODE_WHEN_DIRTY);
		
		this.requestRender();
	}
	
	// Image Operations
	
    public void setOnImageBitmapLoadedListener( OnImageBitmapLoaded listener ) {
    	mCurrentRenderer.setOnImageBitmapLoadedListener(listener);
    }
    
    public void setOnRenderCompletedListener( OnRenderCompletedListener listener ) {
    	mCurrentRenderer.setOnRenderCompletedListener(listener);
    }
	
    public void requestBitmap( OnBitmapReadOutCompleted listener, Bitmap bitmap ) {
    	mCurrentRenderer.setOnBitmapReadOutCompleted(listener);
    	final Bitmap finalBitmap = bitmap;
    	
		this.queueEvent(new Runnable(){
			@Override
			public void run(){
				mCurrentRenderer.requestBitmap(finalBitmap);
			}
		});
    }
	
	public void setImage(Bitmap bitmap){
		final GLImageViewRenderer renderer = mCurrentRenderer;
		final Bitmap outBitmap = bitmap;
		
		this.queueEvent(new Runnable(){
			@Override
			public void run(){
				renderer.setImage(outBitmap);
			}
		});
	}
	
	// Touch Events
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
	    mScaleDetector.onTouchEvent(event);
		if (!mScaleDetector.isInProgress() ) {
			mGestureDetector.onTouchEvent(event);
		}

		int action = event.getAction();
		switch ( action & MotionEvent.ACTION_MASK ) {
			case MotionEvent.ACTION_UP:
			{
				if ( mCurrentRenderer.getAbsoluteScale() < mCurrentRenderer.getMinZoomScale() ) {
					zoomTo( mCurrentRenderer.getMinZoomScale(), 0, 0, 50 );
				}else if (mCurrentRenderer.getAbsoluteScale() > mCurrentRenderer.getMaxZoomScale()){
					zoomTo( mCurrentRenderer.getMaxZoomScale(), mLastValidCenter.x, mLastValidCenter.y, 50 );
				}
				break;
			}
			default:
				break;
		}
		
	    return true;
	}
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
	    @Override
	    public boolean onScale(ScaleGestureDetector detector) {	    	
	      return GLImageView.this.onScale(detector);
	    }
	}
	
	public class GestureListener extends GestureDetector.SimpleOnGestureListener {
		
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			// TODO Auto-generated method stub
			return GLImageView.this.onDoubleTap(e);
		}
		
		@Override
		public boolean onScroll( MotionEvent e1, MotionEvent e2, float distanceX, float distanceY ) {
			return GLImageView.this.onScroll( e1, e2, distanceX, distanceY );
		}

		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) {
			return GLImageView.this.onFling( e1, e2, velocityX, velocityY );
		}
	}
	
	// Touch helpers
	
	public boolean onDoubleTap(MotionEvent e1){
		float centerX = e1.getX();
		float centerY = e1.getY();
		
		float currentScale = mCurrentRenderer.getAbsoluteScale();
		PointF currentTranslation = mCurrentRenderer.getAbsoluteTranslation();
		
		float minScale = mCurrentRenderer.getMinZoomScale();
		float maxScale = mCurrentRenderer.getMaxZoomScale();
		float halfMaxScale = maxScale / 2.f;
		
		float nextScale = 0.f;
		
		if(currentScale < halfMaxScale){
			nextScale = halfMaxScale;
		}else if (currentScale < maxScale){
			nextScale = maxScale;
		}else{
			nextScale = minScale;
		}
		
		float scaleAdjust = nextScale / currentScale;
		
		float dx = (1 - 2 * centerX / (float)mCurrentRenderer.getCurrentWidth());
		float dy = (2 * centerY / (float)mCurrentRenderer.getCurrentHeight() - 1);

		float newProposedCenterX = (dx + currentTranslation.x) * scaleAdjust;
		float newProposedCenterY = (dy + currentTranslation.y) * scaleAdjust;
		
		PointF newCenter = mCurrentRenderer.getConstrainedTranslation(newProposedCenterX, newProposedCenterY, nextScale);
		
		zoomTo(nextScale, newCenter.x, newCenter.y, 300);
		
		mLastValidCenter = newCenter;
		
		return false;
	}

	public boolean onScroll( MotionEvent e1, MotionEvent e2, float distanceX, float distanceY ) {
		if ( e1 == null || e2 == null ) return false;
		if ( e1.getPointerCount() > 1 || e2.getPointerCount() > 1 ) return false;
		if ( mScaleDetector.isInProgress() ) return false;
		Log.i(LOG_TAG, "on scroll");
		
		float dx = -2 * distanceX / (float)mCurrentRenderer.getCurrentWidth();
		float dy =  2 * distanceY / (float)mCurrentRenderer.getCurrentHeight();
		
		this.mCurrentRenderer.postTranslateByAmount(dx, dy);
										
		mLastValidCenter = mCurrentRenderer.getAbsoluteTranslation();
		
		this.requestRender();
		
		return true;
	}
	
	public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) {
		if ( e1 == null || e2 == null ) return false;
		if ( e1.getPointerCount() > 1 || e2.getPointerCount() > 1 ) return false;
		if ( mScaleDetector.isInProgress() ) return false;
		Log.i(LOG_TAG, "on fling");

		float distanceX = e2.getX() - e1.getX();
		float distanceY = e2.getY() - e1.getY();

		if ( Math.abs( velocityX ) > 800 || Math.abs( velocityY ) > 800 ) {
			PointF originalTranslation = mCurrentRenderer.getAbsoluteTranslation();
			
			final float originalX = originalTranslation.x;
			final float originalY = originalTranslation.y;
			
			float rawNewX = originalX + 2 * distanceX / (float)mCurrentRenderer.getCurrentWidth();
			float rawNewY = originalY - 2 * distanceY / (float)mCurrentRenderer.getCurrentHeight();
			
			PointF newTranslation = mCurrentRenderer.getConstrainedTranslation(rawNewX, rawNewY);
			
			mLastValidCenter = newTranslation;
			
			final float newX = newTranslation.x;
			final float newY = newTranslation.y;
			
			final long startTime = System.currentTimeMillis();
			final long durationMs = 300;
			
			final GLImageView glImageView = this;
			
			this.post( new Runnable() {
				@Override
				public void run() {
					long now = System.currentTimeMillis();
					double currentMs = Math.min( durationMs, now - startTime );
					float dx = (float) mEasing.easeOut( currentMs, 0, newX - originalX, durationMs );
					float dy = (float) mEasing.easeOut( currentMs, 0, newY - originalY, durationMs );
					glImageView.mCurrentRenderer.setAbsoluteTranslation(originalX + dx, originalY + dy);
					glImageView.requestRender();
					if(currentMs < durationMs ){
						glImageView.postDelayed(this, 10);
					}
					
//					if(currentMs < durationMs ){
//						final Runnable runnable = this;
//						OnRenderCompletedListener listener = new OnRenderCompletedListener() {
//							@Override
//							public void onRenderCompleted() {
//								glImageView.post(runnable);
//							}
//						};
//						mCurrentRenderer.setOnRenderCompletedListener(listener);
//					}else{
//						mCurrentRenderer.setOnRenderCompletedListener(null);
//					}
				}
			} );
			
			// Cache runnable and cancel it if touches happen
			
			return true;
		}
		
		return false;
	}
	
	public boolean onScale(ScaleGestureDetector detector){
		float scale = detector.getScaleFactor(); 	
		float centerX = 2 * detector.getFocusX() / (float) mCurrentRenderer.getCurrentWidth() - 1;
		float centerY =  1 - 2 * detector.getFocusY() / (float) mCurrentRenderer.getCurrentHeight();
		if(mCurrentRenderer.getAbsoluteScale() < mCurrentRenderer.getMaxZoomScale() * GLImageViewRenderer.mAllowableZoomOvershot){
			this.mCurrentRenderer.postScaleByAmount(scale, centerX, centerY);
			if(mCurrentRenderer.getAbsoluteScale() <= mCurrentRenderer.getMaxZoomScale()){
				mLastValidCenter = mCurrentRenderer.getAbsoluteTranslation();
			}
			this.requestRender();
		}
     
        return true;
	}
	
	public void zoomTo( float scale, float centerX, float centerY, long duration) {
		Log.i(LOG_TAG, "zoom to");
		
		final float originalScale = mCurrentRenderer.getAbsoluteScale();
		PointF center = mCurrentRenderer.getAbsoluteTranslation();
		final float originalCenterX = center.x;
		final float originalCenterY = center.y;
		
		final float newScale = scale;
		final float newCenterX = centerX;
		final float newCenterY = centerY;
		
		final long startTime = System.currentTimeMillis();
		final long durationMs = duration;
		
		final GLImageView glImageView = this;
		
		this.post( new Runnable() {
			@Override
			public void run() {
				long now = System.currentTimeMillis();
				double currentMs = Math.min( durationMs, now - startTime );
				float dScale = (float) mEasing.easeOut( currentMs, 0, newScale - originalScale, durationMs );
				float dx = (float) mEasing.easeOut( currentMs, 0, newCenterX - originalCenterX, durationMs );
				float dy = (float) mEasing.easeOut( currentMs, 0, newCenterY - originalCenterY, durationMs );
				glImageView.mCurrentRenderer.setAbsoluteScale(originalScale + dScale);
				glImageView.mCurrentRenderer.setAbsoluteTranslation(originalCenterX + dx, originalCenterY + dy);
				glImageView.requestRender();
				if(currentMs < durationMs ){
					glImageView.postDelayed(this, 10);
				}
				
//				if(currentMs < durationMs ){
//					final Runnable runnable = this;
//					OnRenderCompletedListener listener = new OnRenderCompletedListener() {
//						@Override
//						public void onRenderCompleted() {
//							glImageView.post(runnable);
//						}
//					};
//					mCurrentRenderer.setOnRenderCompletedListener(listener);
//				}else{
//					mCurrentRenderer.setOnRenderCompletedListener(null);
//				}
			}
		} );			
	}
}