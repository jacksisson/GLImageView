package com.aviary.glimageview;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

public class SimpleGLImageProgram implements GLImagingProgram{

	private static final String LOG_TAG = "SimpleGLImageProgram";
	private static final int FLOAT_BYTE_LENGTH = 4;

	// Shaders
	private String mVertexShader = "" +
	"attribute vec4 a_position;" + 
    "attribute vec2 a_texCoord;" + 
    "varying highp vec2 v_texCoord;" + 
    "void main(void) {" +
    "    v_texCoord = a_texCoord;" +
    "    gl_Position = a_position;" +
    "}";
	
	private String mFragmentShader = "" +
    "		precision mediump float;" + 
    "       uniform lowp sampler2D u_sampler;" +
    "       varying highp vec2 v_texCoord;" +   
    "       void main(void) { " +
    "           gl_FragColor = texture2D(u_sampler, v_texCoord);" +
    "       }";
	
	// GL Objects
	private int mProgram = 0;
	private FloatBuffer mVertexBuffer;
	private int mPositionAttributeLocation;
	private int mTexCoordAttributeLocation;
	private int mSamplerUniformLocation;
    private int mCurrentTextureId;

	// Standard Vertices
    private float[] mVertices = {
            1.f, -1.f,  0.0f, 1.f, 1.f,
            -1.f, -1.f, 0.0f, 0.f, 1.f,
            1.f,  1.f,  0.0f, 1.f, 0.f,
            -1.f, 1.f,  0.0f, 0.f, 0.f
        };
	
	public void setup(){
		Log.i(LOG_TAG, "Setup");
		
		mProgram = createProgram();
        mPositionAttributeLocation = GLES20.glGetAttribLocation(mProgram, "a_position");
        mTexCoordAttributeLocation = GLES20.glGetAttribLocation(mProgram, "a_texCoord");
        mSamplerUniformLocation = GLES20.glGetUniformLocation(mProgram, "u_sampler");
        
        GLES20.glDisable(GL10.GL_CULL_FACE);
        
        mVertexBuffer = ByteBuffer.allocateDirect(mVertices.length * FLOAT_BYTE_LENGTH).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(mVertices);
        mVertexBuffer.position(0);
     }
	
	public void setFramebufferSize(int width, int height) {
		// This simple program doesn't use this information
	}
	
	public void setBitmap(Bitmap bitmap){
		Log.i(LOG_TAG, "Set Bitmap");
		
        int textures[] = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        mCurrentTextureId = textures[0];
	}
	
	public void render(Matrix transformMatrix, RectF imageBoundsRect){
		Log.i(LOG_TAG, "Render");
		
		if(mCurrentTextureId == 0){
			return;
		}
		
//		GLES20.glClearColor(1.f, 0.f, 0.f, 1.f);
		GLES20.glClearColor(0.f, 0.f, 0.f, 0.f);
	    GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT);
	    
        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mCurrentTextureId);
        
        GLES20.glUniform1i(mSamplerUniformLocation, 0);
        
        Log.i(LOG_TAG, "Rect: " + imageBoundsRect);
        
        float points[] = { imageBoundsRect.right, imageBoundsRect.bottom, 
        			imageBoundsRect.left, imageBoundsRect.bottom,
        			imageBoundsRect.right, imageBoundsRect.top,
        			imageBoundsRect.left, imageBoundsRect.top};
        
        transformMatrix.mapPoints(points);
        
        mVertices[0] = points[0];
        mVertices[1] = points[1];
        mVertices[5] = points[2];
        mVertices[6] = points[3];
        mVertices[10] = points[4];
        mVertices[11] = points[5];
        mVertices[15] = points[6];
        mVertices[16] = points[7];
        
        mVertexBuffer.position(0);
        mVertexBuffer.put(mVertices);
                    
        mVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mPositionAttributeLocation, 3, GLES20.GL_FLOAT, false, 5 * FLOAT_BYTE_LENGTH, mVertexBuffer);
        mVertexBuffer.position(3);
        GLES20.glVertexAttribPointer(mTexCoordAttributeLocation, 2, GLES20.GL_FLOAT, false, 5 * FLOAT_BYTE_LENGTH, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(mPositionAttributeLocation);
        GLES20.glEnableVertexAttribArray(mTexCoordAttributeLocation);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}
	
	public void readImage(Bitmap bitmap) {
		// This does nothing in this demo program.
	}
	
	// Program Utils
	 private int createProgram()
    {
        int program = GLES20.glCreateProgram();

        int vertexShader = getShader(GLES20.GL_VERTEX_SHADER, mVertexShader);
        int fragmentShader = getShader(GLES20.GL_FRAGMENT_SHADER, mFragmentShader);

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);

        Log.i(LOG_TAG, "Program " + program);
        
        return program;
    }

    private int getShader(int type, String shaderSource) 
    {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderSource);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) 
        {
            Log.e("opengl", "Could not compile shader");
            Log.e("opengl", GLES20.glGetShaderInfoLog(shader));
            Log.e("opengl", shaderSource);
        }

        return shader;
    }
}
