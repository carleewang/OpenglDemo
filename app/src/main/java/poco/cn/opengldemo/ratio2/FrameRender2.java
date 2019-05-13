package poco.cn.opengldemo.ratio2;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import androidx.annotation.NonNull;
import poco.cn.medialibs.gles.GlUtil;
import poco.cn.opengldemo.ratio.FrameDraw;

/**
 * Created by lgd on 2019/5/7.
 */
public class FrameRender2 implements GLSurfaceView.Renderer
{
    private FrameDraw mFrameDraw;
    private Context mContext;

    private int mTextureId = GlUtil.NO_TEXTURE;
    private Bitmap mBitmap;

//    RenderInfo

    private final float[] mProjectMatrix = new float[16];


    // 缩放矩阵，用于改变glviewport绘制
    private float[] mTempScaleMatrix = new float[16];
    //临时矩阵
    private final float[] mTempModelMatrix = new float[16];
    private final float[] mTempTexMatrix = new float[16];

    /**
     * 是否是铺满 Surface 播放，主要用在全屏播放和进入画幅选择页面时
     *
     *    if true  howRatio 只是在viewRatio的区域限制
     *
     *    if false 在showRatio是实际视区, videoRatio需要投影矩阵CENER_CROP映射在视区，乘以缩放矩阵
     */
    private boolean isFullMode;

    /**
     * 显示 Surface 距离左边的距离，即左边黑色遮罩的宽度
     */
    private int mLeftMargin;

    /**
     * 显示 Surface 距离上面的距离，即上面黑色遮罩的高度
     */
    private int mTopMargin;


    /**
     * 整个 Surface 的大小
     */
    private int mViewWidth;
    private int mViewHeight;

    /**
     * 当前显示画面的大小
     */
    private int mShowWidth;
    private int mShowHeight;


    private float showRatio = 1f;  //  显示页面的范围
    private ImagePlayInfo2 mImagePlayInfo;

    public FrameRender2(@NonNull Context context)
    {
        mContext = context;
        Matrix.setIdentityM(mTempScaleMatrix, 0);
        Matrix.setIdentityM(mProjectMatrix, 0);
    }

    public void setBitmap(Bitmap bitmap)
    {
        this.mBitmap = bitmap;
        if (mTextureId != GlUtil.NO_TEXTURE)
        {

        }
        mTextureId = GlUtil.NO_TEXTURE;
    }

    /**
     * 更换画幅时调用，用于更新 Surface 显示的信息
     *
     * @param playRatio  视频画幅
     * @param leftMargin 显示 Surface 距离左边的距离
     * @param leftRatio  占整个 Surface 大小的比例
     * @param topMargin  显示 Surface 距离上面的距离
     * @param topRatio   占整个 Surface 大小的比例
     */
    public void setLeftAndTop(float playRatio, int leftMargin, float leftRatio, int topMargin, float topRatio)
    {
        showRatio = playRatio;
        mLeftMargin = leftMargin;
        mTopMargin = topMargin;

        initShowSize();
        initProject();

        //本来在glviewport（0,0,mViewWidth,mViewHeight）绘制，现在在 glviewport(mLeftMargin,mTopMargin,mShowWidth,mShowHeight) 绘制
        //视图缩放了leftRatio,TopRatio,但要保持图像形状（自己画图模拟一下），顶点坐标缩放 1/leftRatio
        Matrix.setIdentityM(mTempScaleMatrix, 0);
        // 计算缩放矩阵，和计算保存视频时的 model matrix 一样的计算方式 ，
        float scaleX = 1;
        float scaleY = 1;
        if (leftRatio != 0)
        {
            scaleX = 1 / Math.abs(leftRatio);
        }
        if (topRatio != 0)
        {
            scaleY = 1 / Math.abs(topRatio);
        }
        Matrix.scaleM(mTempScaleMatrix, 0, scaleX, scaleY, 1f);
    }

    private void initProject()
    {
        if (mViewWidth > 0 && mViewHeight > 0 && showRatio > 0)
        {
            Matrix.setIdentityM(mProjectMatrix, 0);
            float ratio = mViewWidth / (float) mViewHeight;
            //下面也可以使用摄像机和投影方式，    大致原理一样，顶点缩放x轴或y轴保持不变形
            if (showRatio > ratio)
            {
                //自行画图，  showRatio是内框，ratio是外框， 现在x轴铺满ratio， 缩放y轴
                Matrix.scaleM(mProjectMatrix, 0, 1f, ratio, 1f);
            } else
            {
                //showRatio y轴比例大，保持y轴不变，缩放x轴
                Matrix.scaleM(mProjectMatrix, 0, 1 / ratio, 1f, 1f);
            }
        }
    }

    private void initShowSize()
    {
        if (isFullMode)
        {
            mShowWidth = mViewWidth;
            mShowHeight = mViewHeight;
        } else
        {
            mShowWidth = mViewWidth - 2 * mLeftMargin;
            mShowHeight = mViewHeight - 2 * mTopMargin;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        mFrameDraw = new FrameDraw(mContext);

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        GLES20.glViewport(0, 0, width, height);
        mViewWidth = width;
        mViewHeight = height;

        initShowSize();
        initProject();
    }

    @Override
    public void onDrawFrame(GL10 gl)
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(1, 0, 0, 0.2f);
        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);
        if (mTextureId == GlUtil.NO_TEXTURE && mBitmap != null)
        {
            mTextureId = GlUtil.createTexture(mBitmap);
        }
        if (mTextureId != GlUtil.NO_TEXTURE)
        {
            Matrix.setIdentityM(mTempModelMatrix, 0);
            Matrix.setIdentityM(mTempTexMatrix, 0);
            //纹理坐标系和androd坐标系y轴翻转，这里翻转一下
            mTempTexMatrix[5] = -1;
            mTempTexMatrix[13] = 1;
//            Matrix.multiplyMM(mTempModelMatrix, 0, mModelMatrix, 0, mTempModelMatrix, 0);
            Matrix.multiplyMM(mTempModelMatrix, 0, mImagePlayInfo.modelMatrix, 0, mImagePlayInfo.texMatrix, 0);
            Matrix.multiplyMM(mTempTexMatrix, 0, mTempTexMatrix, 0, mImagePlayInfo.texMatrix, 0);
            Matrix.multiplyMM(mTempModelMatrix, 0, mProjectMatrix, 0, mTempModelMatrix, 0);
            if (!isFullMode)
            {
                Matrix.multiplyMM(mTempModelMatrix, 0, mTempScaleMatrix, 0, mTempModelMatrix, 0);
                GLES20.glViewport(mLeftMargin, mTopMargin, mShowWidth, mShowHeight);
            }

//            Matrix.scaleM(mTexMatrix,0,2f,2f,1f);
//            Matrix.translateM(mTexMatrix,0,0.1f,0.1f,0f);
            mFrameDraw.draw(mTextureId, mTempModelMatrix, mTempTexMatrix);
        }
    }

    public void setFullMode(boolean fullMode)
    {
        isFullMode = fullMode;
        initShowSize();
    }

    public void setImageInfo(ImagePlayInfo2 playInfo)
    {
        mImagePlayInfo = playInfo;
    }
}
