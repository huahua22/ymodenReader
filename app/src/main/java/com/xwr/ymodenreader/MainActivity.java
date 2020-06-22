package com.xwr.ymodenreader;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.xwr.ymodenreader.ymoden.YModem;
import com.xwr.ymodenreader.ymoden.YModemListener;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
  byte[] flash_cmd = {(byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0x96, 0x69, 0x00, 0x06, 0x55, 0x50, 0x1A, 0x2A, 0x3A, 0x09};
  @BindView(R.id.et_file_show)
  EditText mEtFileShow;
  @BindView(R.id.btn_file_select)
  Button mBtnFileSelect;
  @BindView(R.id.tv_progress)
  TextView mTvProgress;
  @BindView(R.id.pb_progress)
  ProgressBar mPbProgress;
  @BindView(R.id.btn_stat_download)
  Button mBtnStatDownload;
  @BindView(R.id.tv_result)
  TextView mTvResult;
  private File mFirmwareFile;
  private YModem yModem;
  @SuppressLint("HandlerLeak")
  private Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case 0:
          mTvResult.append("success");
          break;
        case 1:
          float pro = (float) msg.obj;
          mTvProgress.setText((int) pro + "%");
          break;
        case -1:
          String reason = (String) msg.obj;
          mTvResult.append("fail:" + reason);
          break;
        default:
          break;
      }
    }
  };


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
    initData();
  }

  private void initData() {
    mTvResult.setMovementMethod(ScrollingMovementMethod.getInstance());
    SerialPortUtil.getInstance().setOnDataReceiveListener(new SerialPortUtil.OnDataReceiveListener() {
      @Override
      public void onDataReceive(byte[] buffer, int size) {
        if (size > 0) {
          yModem.onReceiveData(buffer);
        }

      }
    });

  }


  @Override
  protected void onResume() {
    super.onResume();
    if (!SerialPortUtil.getInstance().isOpen()) {
      SerialPortUtil.getInstance().onCreate();
    }
  }

  @OnClick({R.id.btn_file_select, R.id.btn_stat_download})
  public void onViewClicked(View view) {
    switch (view.getId()) {
      case R.id.btn_file_select:
        selectFileDialog();
        break;
      case R.id.btn_stat_download:
        startFlash();
        break;
    }
  }

  private void selectFileDialog() {
    DialogUtils.select_file(this, new DialogUtils.DialogSelection() {
      @Override
      public void onSelectedFilePaths(String[] files) {
        if (files.length == 1) {
          mEtFileShow.setText(files[0]);
          mFirmwareFile = new File(files[0]);
        }
      }
    });

  }

  private void startFlash() {
    mTvResult.setText("");
    mTvProgress.setText("0%");
    mPbProgress.setProgress(0);
    mPbProgress.setVisibility(View.VISIBLE);
    //发送升级指令
    boolean result = SerialPortUtil.getInstance().sendCmds(flash_cmd);
    Log.d("xwr", "" + result);
    if (result) {
      if (mFirmwareFile != null && mFirmwareFile.exists()) {
        yModem = new YModem.Builder()
          .with(this)
          .filePath(mFirmwareFile.getPath()) //存放到手机的文件路径 stroge/0/.../xx.bin 这种路径
          .fileName(mFirmwareFile.getName().substring(0, mFirmwareFile.getName().lastIndexOf(".")))
          .checkMd5("") //Md5可以写可以不写 看自己的通讯协议
          .sendSize(1024) //可以修改成你需要的大小
          .callback(new YModemListener() {
            @Override
            public void onDataReady(byte[] data) {
              SerialPortUtil.getInstance().sendCmds(data);
            }

            @Override
            public void onProgress(int currentSent, int total) {
              //进度条处理
              float pro = (float) currentSent / (float) total * 100;
              if (pro >= 100) {
                pro = 100;
              }
              mPbProgress.setProgress((int) pro);
              Message message = new Message();
              message.what = 1;
              message.obj = pro;
              mHandler.sendMessage(message);
            }

            @Override
            public void onSuccess() {
              //成功的显示
              Message msg = new Message();
              msg.what = 0;
              mHandler.sendMessage(msg);
            }

            @Override
            public void onFailed(String reason) {
              Message msg = new Message();
              msg.what = -1;
              msg.obj = reason;
              mHandler.sendMessage(msg);
            }
          }).build();
        yModem.start();
      } else {
        showToast(getString(R.string.valid_file));
      }
    } else {
      mTvResult.setText("升级指令写入失败");
    }
  }

  public void showToast(String str) {
    Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (yModem != null) {
      yModem.stop();
    }
    SerialPortUtil.getInstance().closeSerialPort();
  }
}
