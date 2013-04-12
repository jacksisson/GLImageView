package com.aviary.glimageview;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

public class GLImageViewRenderer implements GLSurfaceView.Renderer {
	private final static String LOG_TAG = "GLImageView";
	public static final float mAllowableZoomOvershot = 1.f / 0.9f;

    // Cached sizes
    private int mCurrentBitmapWidth = 0;
    private int mCurrentBitmapHeight = 0;
	private int mCurrentWidth = 0;
	private int mCurrentHeight = 0;
	
	// Resizing Matrix
	private float mMinZoomScale = 1.f;
	private float mMaxZoomScale = 5.f;
	protected Matrix mTransformMatrix;
	protected final float[] mMatrixValues = new float[9];
	protected RectF mImageBoundsRect;
	
	// Rendering
    GLImagingProgram mGlProgram;
    Bitmap mPendingBitmapToSet;
    boolean mHasSetupProgram;

    // Listeners
    private OnImageBitmapLoaded mImageLoadedListener;
    private OnBitmapReadOutCompleted mReadOutListener;
    private OnRenderCompletedListener mRenderCompletedListener;
    
    public GLImageViewRenderer(GLImagingProgram program){
    	mGlProgram = program;
    	if(mGlProgram == null){
    		throw new IllegalArgumentException("program cannot be null");
    	}
    }
    
	@Override
	public void onSurfaceCreated( GL10 gl, EGLConfig config ) {
		Log.i( LOG_TAG, "onSurfaceCreated" );
        mTransformMatrix = new Matrix();   
        mImageBoundsRect = new RectF();
                
        mGlProgram.setup();
        mHasSetupProgram = true;
        
        if (mPendingBitmapToSet != null){
        	setImage(mPendingBitmapToSet);
        }
	}
	
	@Override
	public void onSurfaceChanged( GL10 gl, int width, int height ) {
		Log.i( LOG_TAG, "onSurfaceChanged. " + width + "x" + height );
		mCurrentWidth = width;
		mCurrentHeight = height;
		GLES20.glViewport(0, 0, width, height);
		setupImageRect();
		
		mGlProgram.setFramebufferSize(width, height);
	}
    
	@Override
	public void onDrawFrame( GL10 gl ) {
		Log.i(LOG_TAG, "drawFrame");
		mGlProgram.render(mTransformMatrix, mImageBoundsRect);
		if(mRenderCompletedListener != null){
			mRenderCompletedListener.onRenderCompleted();
		}
	}
	
	public void setImage(Bitmap bitmap){
		if(mHasSetupProgram){
			mGlProgram.setBitmap(bitmap);
			mCurrentBitmapWidth = bitmap.getWidth();
			mCurrentBitmapHeight = bitmap.getHeight();
			
			if(mImageLoadedListener != null){
				mImageLoadedListener.onBitmapLoaded(bitmap);
			}
			
			mPendingBitmapToSet = null;
			setupImageRect();
		}else{
			mPendingBitmapToSet = bitmap;
		}
	}
	
	private void setupImageRect(){		
		if(mCurrentBitmapHeight == 0 || mCurrentBitmapWidth == 0 || mCurrentHeight == 0 || mCurrentWidth == 0){
			return;
		}
		
		float glHalfWidth = 1.f;
		float glHalfHeight = 1.f;
		
		float imageAspect = mCurrentBitmapWidth / (float)mCurrentBitmapHeight;
		float renderbufferAspect = mCurrentWidth / (float) mCurrentHeight;
		
		if(imageAspect > renderbufferAspect){
			glHalfWidth = Math.min(1.f, mCurrentBitmapWidth / (float)mCurrentWidth);
			glHalfHeight = glHalfWidth * renderbufferAspect / imageAspect;
		}else{
			glHalfHeight = Math.min(1.f, mCurrentBitmapHeight / (float)mCurrentHeight);
			glHalfWidth = imageAspect / renderbufferAspect;
		}
			
        mImageBoundsRect.set(-glHalfWidth, glHalfHeight, glHalfWidth, -glHalfHeight);
	}
	
	// Matrix Helpers 	
	
	public void postScaleByAmount(float amount){
		mTransformMatrix.getValues(mMatrixValues);
		
		float scale = mMatrixValues[Matrix.MSCALE_X];
		float newScale = scale * amount;
		
		scale = Math.max(mMinZoomScale * 1 / mAllowableZoomOvershot, Math.min(newScale, mMaxZoomScale * mAllowableZoomOvershot));
		
		mMatrixValues[Matrix.MSCALE_X] = scale;
		mMatrixValues[Matrix.MSCALE_Y] = scale;
		
		mTransformMatrix.setValues(mMatrixValues);
		
		constrainTranslation();
	}
	
	public void postScaleByAmount(float amount, float centerX, float centerY){
		mTransformMatrix.postScale(amount, amount, centerX, centerY);
		
		mTransformMatrix.getValues(mMatrixValues);
		
		float scale = mMatrixValues[Matrix.MSCALE_X];
		scale = Math.max(mMinZoomScale * 1 / mAllowableZoomOvershot, Math.min(scale, mMaxZoomScale * mAllowableZoomOvershot));
		
		mMatrixValues[Matrix.MSCALE_X] = scale;
		mMatrixValues[Matrix.MSCALE_Y] = scale;
		
		mTransformMatrix.setValues(mMatrixValues);
		
		constrainTranslation();
	}
	
	public void postTranslateByAmount(float amountX, float amountY){
		mTransformMatrix.getValues( mMatrixValues);

		float transX = mMatrixValues[Matrix.MTRANS_X] + amountX;
		float transY = mMatrixValues[Matrix.MTRANS_Y] + amountY;
		
		mMatrixValues[Matrix.MTRANS_X] = transX;
		mMatrixValues[Matrix.MTRANS_Y] = transY;
		
		mTransformMatrix.setValues(mMatrixValues);
		
		constrainTranslation();
	}
	
	public PointF getAbsoluteTranslation(){
		PointF translation = new PointF();
		mTransformMatrix.getValues( mMatrixValues);
		translation.x = mMatrixValues[Matrix.MTRANS_X];
		translation.y = mMatrixValues[Matrix.MTRANS_Y];
		return translation;
	}
	
	public float getAbsoluteScale(){
		mTransformMatrix.getValues( mMatrixValues);
		float scale = mMatrixValues[Matrix.MSCALE_X];
		return scale;
	}
	
	public PointF getConstrainedTranslation(float transX, float transY){
		return getConstrainedTranslation(transX, transY, getAbsoluteScale());
	}
	
	public PointF getConstrainedTranslation(float transX, float transY, float scale){
		PointF translation = new PointF();
		
		mTransformMatrix.getValues(mMatrixValues);
		
		float scaleX = Math.max(scale * mImageBoundsRect.right, 1.f);
		float scaleY = Math.max(scale * mImageBoundsRect.top, 1.f);

		if(transX < 0){
			translation.x = Math.max(transX, 1 - scaleX);
		}else{
			translation.x = Math.min(transX, scaleX - 1);
		}
		if(transY < 0){
			translation.y = Math.max(transY, 1 - scaleY);
		}else{
			translation.y = Math.min(transY, scaleY - 1);
		}
			
		return translation;
	}
	
	public void setAbsoluteTranslation(float transX, float transY){
		mTransformMatrix.getValues( mMatrixValues);

		mMatrixValues[Matrix.MTRANS_X] = transX;
		mMatrixValues[Matrix.MTRANS_Y] = transY;
		
		mTransformMatrix.setValues(mMatrixValues);

		constrainTranslation();
	}
	
	public void setAbsoluteScale(float scale){
		mTransformMatrix.getValues( mMatrixValues);

		mMatrixValues[Matrix.MSCALE_X] = scale;
		mMatrixValues[Matrix.MSCALE_Y] = scale;
		
		mTransformMatrix.setValues(mMatrixValues);

		constrainTranslation();
	}
	
	private void constrainTranslation(){
		mTransformMatrix.getValues(mMatrixValues);
	
		float transX = mMatrixValues[Matrix.MTRANS_X];
		float transY = mMatrixValues[Matrix.MTRANS_Y];

		PointF newTrans = getConstrainedTranslation(transX, transY);
		
		mMatrixValues[Matrix.MTRANS_X] = newTrans.x;
		mMatrixValues[Matrix.MTRANS_Y] = newTrans.y;
		
		mTransformMatrix.setValues(mMatrixValues);
	}
	
	// Getters
	
	public int getCurrentWidth(){
		return mCurrentWidth;
	}
	
	public int getCurrentHeight(){
		return mCurrentHeight;
	}
	
	public float getMinZoomScale(){
		return mMinZoomScale;
	}
	
	public float getMaxZoomScale(){
		return mMaxZoomScale;
	}
	
	public Matrix getTransformMatrix(){
		return mTransformMatrix;
	}
	
	public RectF getImageBoundsRect(){
		return mImageBoundsRect;
	}
	
	public void requestBitmap(Bitmap bitmap)
	{
		if(mCurrentBitmapWidth == 0 || mCurrentBitmapHeight == 0){ 
			return;
		}
		
		if(bitmap == null){
			bitmap = Bitmap.createBitmap(mCurrentBitmapWidth, mCurrentBitmapHeight, Bitmap.Config.ARGB_8888);
		}
		
		if(bitmap.getWidth() != mCurrentBitmapWidth || bitmap.getHeight() != mCurrentBitmapHeight || bitmap.getConfig() != Bitmap.Config.ARGB_8888){
			throw new IllegalArgumentException("bitmap must be same size and config as the one passed in");
		}
		
		mGlProgram.readImage(bitmap);
		mReadOutListener.onBitmapReadOutCompleted(bitmap);
	}
	
	// Listeners
	
    /**
     * Interface for receiving bitmap load events
     * @author Jack
     *
     */
    public static interface OnImageBitmapLoaded {
    	
    	/**
    	 * Called when bitmap loading process is finished. Always called on dedicated GL rendering queue.
    	 * @param bitmap The bitmap that has finished loading.
    	 */
    	public void onBitmapLoaded( Bitmap bitmap );
    }
    
    
    public void setOnImageBitmapLoadedListener( OnImageBitmapLoaded listener ) {
    	mImageLoadedListener = listener;
    }
    
    public static interface OnBitmapReadOutCompleted {
    	
    	/**
    	 * Called when bitmap loading process is finished. Always called on dedicated GL rendering queue.
    	 * @param bitmap The bitmap that has finished loading.
    	 */
    	public void onBitmapReadOutCompleted( Bitmap bitmap );
    }
    
    
    public void setOnBitmapReadOutCompleted( OnBitmapReadOutCompleted listener ) {
    	mReadOutListener = listener;
    }
    
    public static interface OnRenderCompletedListener {
    	public void onRenderCompleted();
    }
    
    
    public void setOnRenderCompletedListener( OnRenderCompletedListener listener ) {
    	mRenderCompletedListener = listener;
    }
}