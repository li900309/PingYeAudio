package com.cn.cae;

import org.json.JSONException;
import org.json.JSONObject;

import com.cn.cae.util.PcmFileUtil;
import com.iflytek.alsa.AlsaRecorder;
import com.iflytek.alsa.AlsaRecorder.PcmListener;
import com.iflytek.cae.CAEEngine;
import com.iflytek.cae.CAEError;
import com.iflytek.cae.CAEListener;
import com.iflytek.cae.util.res.ResourceUtil;
import com.iflytek.cae.util.res.ResourceUtil.RESOURCE_TYPE;
import com.iflytek.JsonParser;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUnderstanderListener;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.UnderstanderResult;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	final static String TAG = "CAEDemo ";

	// 唤醒资源路径
	String mResPath;
	
	// 抽取的音频保存路径
	String mExtractAudioDir = "/sdcard/CAE16KAudio/";
	
	// 径唤醒成功后抛出的音频保存路
	String mWakeAudioDir = "/sdcard/CAEWakeAudio/";
	
	// 多通道原始音频保存路径
	String mRawAudioDir = "/sdcard/CAERawAudio/";

	TextView mStatus;
	TextView mVol;
	
	EditText mChannelEdit;
	EditText mBeamEdit;
	
	Button mCreateInstance;
	Button mWriteAudio;
	Button mSetRealBeam;
	Button mReset;
	Button mDestroy;
	Button mStopPcmRecord;

	Toast mToast;

	// 抽取音频的通道编号
	int mChannel = 1;
	
	// 波束编号
	int mBeam = 0;
	
	PcmFileUtil mExtractFileUtil;
	PcmFileUtil mWakeFileUtil;
	PcmFileUtil mRawFileUtil;
	
	JSONObject mSyn = new JSONObject();

	// CAE算法引擎
	CAEEngine mCAEEngine;

	// msc语音识别类
	SpeechRecognizer mIat;

	// msc语音合成类
	SpeechSynthesizer mTts;

    //语义理解引擎
    SpeechUnderstander mSpeechUnderstander;

	CAEListener mCAEListener = new CAEListener() {

		@Override
		public void onWakeup(String jsonResult) {
			mStatus.setText("唤醒成功：" + jsonResult);
			Log.d(TAG,"唤醒成功");
			mRawFileUtil.closeWriteFile();
			mRawFileUtil.createPcmFile();
			
			mWakeFileUtil.createPcmFile();
			//mTts.startSpeaking("你好", mTtsListener);

            Log.d(TAG,"开始语义识别");
            int ret;
            ret =  mSpeechUnderstander.startUnderstanding(mSpeechUnderstanderListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("听写失败,错误码：" + ret);
            }
		}

		@Override
		public void onError(CAEError error) {
			mStatus.setText("引擎出错，错误码：" + error.getErrorCode());
		}

		@Override
		public void onAudio(byte[] audioData, int dataLen, int param1,
				int param2) {
			Log.d(TAG,"onAudio:"+dataLen);
			mWakeFileUtil.write(audioData);
			mIat.writeAudio(audioData,0,dataLen);
            mSpeechUnderstander.writeAudio(audioData,0,dataLen);
		}
	};

	// Alsa录音机
	AlsaRecorder mRecorder = AlsaRecorder.createInstance(1);

	// 音频监听器
	PcmListener mPcmListener = new PcmListener() {

		@Override
		public void onPcmData(byte[] data, int dataLen) {
			synchronized (mSyn) {
				if (null != mCAEEngine) {
					mRawFileUtil.write(data, 0, dataLen);
					mCAEEngine.writeAudio(data, dataLen);
					Log.d(TAG,"writeAudio:"+dataLen);
				}
			}
		}

	};

	private void printVol(final int vol) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mVol.setText(vol + "");
			}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// 构建资源路径
		mResPath = ResourceUtil.generateResourcePath(MainActivity.this,
						RESOURCE_TYPE.assets, "dingdong.jet");
		//mResPath = ResourceUtil.generateResourcePath(MainActivity.this,
		//				RESOURCE_TYPE.assets, "hellopy.jet");

		mExtractFileUtil = new PcmFileUtil(mExtractAudioDir);
		mWakeFileUtil = new PcmFileUtil(mWakeAudioDir);
		mRawFileUtil = new PcmFileUtil(mRawAudioDir);
		Log.d(TAG,"创建文件成功");

		initUI();

		// 初始化识别对象
		mIat = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
		setIatParam();
		// 初始化合成对象
		mTts = SpeechSynthesizer.createSynthesizer(MainActivity.this, mTtsInitListener);
		setTtsParam();
        //初始化语义理解对象
        mSpeechUnderstander = SpeechUnderstander.createUnderstander(MainActivity.this, mSpeechUdrInitListener);
        setUnderstanderParam();

	}

	private void initUI() {
		mStatus = (TextView) findViewById(R.id.txt_status);
		mVol = (TextView) findViewById(R.id.txt_vol);
		mChannelEdit = (EditText) findViewById(R.id.edt_channel);
		mBeamEdit = (EditText) findViewById(R.id.edt_beam);
		mCreateInstance = (Button) findViewById(R.id.btn_createInstance);
		mWriteAudio = (Button) findViewById(R.id.btn_writeAudio);
		mSetRealBeam = (Button) findViewById(R.id.btn_set_beam);
		mReset = (Button) findViewById(R.id.btn_reset);
		mDestroy = (Button) findViewById(R.id.btn_destroy);
		mStopPcmRecord = (Button) findViewById(R.id.btn_stopPcmRecord);

		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
        imm.hideSoftInputFromWindow(mChannelEdit.getWindowToken(),0); 
		
		mCreateInstance.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mStatus.setText("创建对象");

				mCAEEngine = CAEEngine.createInstance("cae_5mic", mResPath);
				mCAEEngine.setCAEListener(mCAEListener);
			}
		});

		mWriteAudio.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null == mCAEEngine) {
					showTip("引擎对象为空，请先创建");
					return;
				}
				
				String channel = mChannelEdit.getText().toString();
				if (!TextUtils.isEmpty(channel)) {
					mChannel = Integer.parseInt(channel);
				} else {
					showTip("channel不能为空");
					return;
				}
				
				if (0 == mRecorder.startRecording(mPcmListener)) {
					showTip("开始写入音频");
					Log.d(TAG,"开始写入音频");
//					if (null == mExtractThread) {
//						mExtractThread = new ExtractAudioThread();
//						mExtractThread.start();
//					}
					
					mRawFileUtil.createPcmFile();
//					mExtractFileUtil.createPcmFile();
				}
			}
		});

		mSetRealBeam.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null == mCAEEngine) {
					showTip("引擎对象为空，请先创建");
					return;
				}
				
				String beam = mBeamEdit.getText().toString();
				if (TextUtils.isEmpty(beam)) {
					showTip("beam不能为空");
					return;
				}
				
				mBeam = Integer.parseInt(beam);
				
				mStatus.setText("设置beam, beam=" + mBeam);

				synchronized (mSyn) {
					if (null != mCAEEngine) {
						mCAEEngine.setRealBeam(mBeam);
					}
				}
			}
		});

		mReset.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null == mCAEEngine) {
					showTip("引擎对象为空，请先创建");
					return;
				}
				
				mStatus.setText("重置cae");
				mVol.setText("");
				mWakeFileUtil.closeWriteFile();
				
				synchronized (mSyn) {
					if (null != mCAEEngine) {
						mCAEEngine.reset();
					}
				}
			}
		});

		mDestroy.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null == mCAEEngine) {
					showTip("引擎对象为空，请先创建");
					return;
				}
				
				mStatus.setText("销毁cae");

				mRecorder.stopRecording();
				
				mRawFileUtil.closeWriteFile();
				mWakeFileUtil.closeWriteFile();
				mExtractFileUtil.closeWriteFile();
				
				synchronized (mSyn) {
					mCAEEngine.destroy();
					mCAEEngine = null;
				}
			}
		});

		mStopPcmRecord.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mRawFileUtil.closeWriteFile();
				mExtractFileUtil.closeWriteFile();
				mWakeFileUtil.closeWriteFile();
				
				mRecorder.stopRecording();

			}
		});

		mToast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);
	}

	private void showTip(String tip) {
		if (!TextUtils.isEmpty(tip)) {
			mToast.setText(tip);
			mToast.show();
		}
	}

	class AudioData {
		public byte[] mData;
		public int mDataLen;

		public AudioData(byte[] data, int dataLen) {
			mData = data;
			mDataLen = dataLen;
		}
	}

	/**
	 * 初始化监听器。
	 */
	private InitListener mInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败，错误码：" + code);
			}
		}
	};

	/**
	 * 初始化监听。
	 */
	private InitListener mTtsInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "InitListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败,错误码："+code);
			} else {
				Log.d(TAG, "语音合成器初始化完成");
				// 初始化成功，之后可以调用startSpeaking方法
				// 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
				// 正确的做法是将onCreate中的startSpeaking调用移至这里
			}
		}
	};
    /**
     * 初始化监听器（语音到语义）。
     */
    private InitListener mSpeechUdrInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "speechUnderstanderListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码："+code);
            }
        }
    };
	private void setIatParam() {
		// 清空参数
		mIat.setParameter(SpeechConstant.PARAMS, null);

		// 设置听写引擎
		mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
		// 设置返回结果格式
		mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");


		// 设置语言
		mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
		// 设置语言区域
		mIat.setParameter(SpeechConstant.ACCENT, "mandarin");


		// 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
		mIat.setParameter(SpeechConstant.VAD_BOS, "5000");

		// 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
		mIat.setParameter(SpeechConstant.VAD_EOS, "1800");

		// 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
		mIat.setParameter(SpeechConstant.ASR_PTT, "1");

		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
		mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");

		showTip("设置Iat参数完成");
		Log.d(TAG, "设置Iat参数完成");
	}
	private void setTtsParam(){
		// 清空参数
		mTts.setParameter(SpeechConstant.PARAMS, null);
		// 根据合成引擎设置相应参数

		mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
		// 设置在线合成发音人
		mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan");
		//设置合成语速
		mTts.setParameter(SpeechConstant.SPEED, "50");
		//设置合成音调
		mTts.setParameter(SpeechConstant.PITCH, "50");
		//设置合成音量
		mTts.setParameter(SpeechConstant.VOLUME, "50");

		//设置播放器音频流类型
		mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
		// 设置播放合成音频打断音乐播放，默认为true
		mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		//mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
	}
	private void setIatAudioSourcePath(String path) {
		// 清空参数
		/*
		File pcmPath = new File(path);

		if (!pcmPath.exists()) {
			Log.d(TAG, "设置设置AUDIO_SOURCE文件路径错误");
			return;
		}*/
		String sdPath = Environment.getExternalStorageDirectory().getPath();
		Log.d(TAG, "外部存储路径"+ sdPath);
		path = "/sdcard/1.pcm";
		//mIat.setParameter(SpeechConstant.ASR_SOURCE_PATH, path);
		mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-2");
		mIat.setParameter(SpeechConstant.ASR_SOURCE_PATH, "/sdcard/1.pcm");
		Log.d(TAG, "设置AUDIO_SOURCE"+ path);
	}
	private void setIatAudioSourceStream() {
		mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
		Log.d(TAG, "设置AUDIO_SOURCE From Stream");
	}
    public void setUnderstanderParam(){
        String lang = "mandarin";

        // 设置语言
        mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置语言区域
        mSpeechUnderstander.setParameter(SpeechConstant.ACCENT, lang);
        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_BOS, "4000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_EOS, "1000");

        // 设置标点符号，默认：1（有标点）
        mSpeechUnderstander.setParameter(SpeechConstant.ASR_PTT, "1");

        // 设置AUDIO_SOURCE From Stream
        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
        // 设置语义情景
        mSpeechUnderstander.setParameter(SpeechConstant.SCENE, "main");
    }

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
			Log.d(TAG,"开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
            showTip(error.getPlainDescription(true));
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
			Log.d(TAG,"结束说话");
			mIat.stopListening();
			mRawFileUtil.closeWriteFile();
			mWakeFileUtil.closeWriteFile();

			mRecorder.stopRecording();
		}

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
			if (results == null) {
				Log.d(TAG,"返回result为空");
				return;
			}

			printResult(results);
            showTip("听写成功");
			Log.d(TAG,"听写成功");
            if (isLast) {
                // TODO 最后的结果
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            //Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

	private void printResult(RecognizerResult results) {
		String text = JsonParser.parseIatResult(results.getResultString());

		String sn = null;
		// 读取json结果中的sn字段
		try {
			JSONObject resultJson = new JSONObject(results.getResultString());
			sn = resultJson.optString("sn");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		Log.d(TAG, text);
		mVol.append(text);
	}

	/**
	 * 合成回调监听。
	 */
	private SynthesizerListener mTtsListener = new SynthesizerListener() {

		@Override
		public void onSpeakBegin() {
			showTip("开始播放");
		}

		@Override
		public void onSpeakPaused() {
			showTip("暂停播放");
		}

		@Override
		public void onSpeakResumed() {
			showTip("继续播放");
		}

		@Override
		public void onBufferProgress(int percent, int beginPos, int endPos,
									 String info) {
			/*
			// 合成进度
			mPercentForBuffering = percent;
			showTip(String.format(getString(R.string.tts_toast_format),
					mPercentForBuffering, mPercentForPlaying));
			*/
		}

		@Override
		public void onSpeakProgress(int percent, int beginPos, int endPos) {
			/*
			// 播放进度
			mPercentForPlaying = percent;
			showTip(String.format(getString(R.string.tts_toast_format),
					mPercentForBuffering, mPercentForPlaying));
			*/
		}

		@Override
		public void onCompleted(SpeechError error) {
			if (error == null) {
				showTip("播放完成");
			} else if (error != null) {
				showTip(error.getPlainDescription(true));
			}
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};

    /**
     * 语义理解回调。
     */
    private SpeechUnderstanderListener mSpeechUnderstanderListener = new SpeechUnderstanderListener() {

        @Override
        public void onResult(final UnderstanderResult result) {
            if (null != result) {
                Log.d(TAG, result.getResultString());

                // 显示
                String text = result.getResultString();
                if (!TextUtils.isEmpty(text)) {
                    mVol.setText(text);
                }
            } else {
                showTip("识别结果不正确。");
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, data.length+"");
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            Log.e(TAG,"语义识别错误，错误码："+error.getErrorCode());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };
}
