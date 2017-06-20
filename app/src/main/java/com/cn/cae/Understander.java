package com.cn.cae;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.JsonReader;
import android.util.Log;
import android.app.Activity;

import com.iflytek.cloud.UnderstanderResult;

import android.app.Service;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.LinkedList;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Created by liyunzhi on 2017/6/19.
 */

public class Understander {

    static String TAG = "resUnderstander";
    boolean inited = false;
    MediaPlayer mediaPlayer = new MediaPlayer();
    private AudioManager audioManager = (AudioManager)SpeechApp.getContext().getSystemService(Context.AUDIO_SERVICE);
    int playStatus;
    LinkedList playList = new LinkedList();

    class understanderRes {
        public int rc;
        public JSONObject error;
        public String text;
        public String vendor;
        public String service;
        public JSONArray semantic;
        public JSONObject data;
        public String answer;
        public String dialog_stat;
        public JSONObject moreResults;
    }

    public int parseResult(String JSONResult) {
        understanderRes res = new understanderRes();
        try {
            JSONTokener jsonParser = new JSONTokener(JSONResult);
            JSONObject joResult = new JSONObject(jsonParser);

            res.rc = joResult.getInt("rc");
            res.error = joResult.optJSONObject("error");
            res.text = joResult.getString("text");
            res.vendor = joResult.optString("vendor");
            res.service = joResult.getString("service");
            res.semantic = joResult.optJSONArray("semantic");
            res.data = joResult.optJSONObject("data");
            res.answer = joResult.optString("answer");
            res.dialog_stat = joResult.optString("dialog_stat");
            res.moreResults = joResult.optJSONObject("moreResult");
            inited = true;

            handleResult(res);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public Understander() {
        // 初始化媒体播放器
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            @Override
            public void onCompletion(MediaPlayer mp) {
                // 检查播放列表
                checkMediaPlayList();
            }
        });
        // 清空播放列表
        playList.clear();
    }

    public int checkMediaPlayList() {
        try {
            if (playList.isEmpty()) {
                Log.d(TAG, "播放列表为空");
                return 0;
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(playList.pop().toString());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public int mediaStart() {
        mediaPlayer.start();
        return 0;
    }
    public int mediaPause() {
        mediaPlayer.pause();
        return 0;
    }
    public int mediaStop() {
        playList.clear();
        mediaPlayer.stop();
        return 0;
    }
    public int handleResult(understanderRes res) {
        int ret = 0;
        JSONArray dataResult;

        if (res.rc != 0) {
            Log.e(TAG, "UnderstandResult 解析失败");
            return -1;
        }

        // tts 语音合成answer字段

        switch (res.service) {
            case "news":
                break;
            case "story":
                playList.clear();
                // 根据 Service 类型处理 data 字段
                if (res.data == null) {
                    Log.e(TAG,"返回数据为空");
                    return -1;
                }
                dataResult = res.data.optJSONArray("result");
                for (int i = 0; i < dataResult.length(); i++ ){
                    JSONObject t = dataResult.optJSONObject(i);
                    if (t != null) {
                        String playUrl = t.optString("playUrl");
                        if (playUrl == null)
                            continue;
                        playList.addLast(playUrl);
                    }
                }
                checkMediaPlayList();
                break;

            case "joke":
                if (res.data == null) {
                    Log.e(TAG,"返回数据为空");
                    return -1;
                }
                dataResult = res.data.optJSONArray("result");

                playList.clear();
                for (int i = 0; i < dataResult.length(); i++ ){
                    JSONObject t = dataResult.optJSONObject(i);
                    if (t != null) {
                        String mp3Url = t.optString("mp3Url");
                        if (mp3Url == null)
                            continue;
                        playList.addLast(mp3Url);
                    }
                }

                checkMediaPlayList();
                break;
            case "musicX":

            case "cmd":
                try {
                    if (res.semantic.optJSONObject(0).optString("intent").equals("INSTRUCTION")) {
                        if (res.semantic.optJSONObject(0).getJSONArray("slots").getJSONObject(0).getString("name").equals("insType")) {
                            String cmd = res.semantic.optJSONObject(0).getJSONArray("slots").getJSONObject(0).getString("value");
                            switch (cmd) {
                                case "volume_plus":
                                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE, AudioManager.FX_FOCUS_NAVIGATION_UP);
                                    mediaStart();
                                    break;
                                case "volume_minus":
                                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER, AudioManager.FX_FOCUS_NAVIGATION_UP);
                                    mediaStart();
                                    break;
                                case "pause":
                                    mediaStop();
                                    break;
                            }
                        }
                    }
                } catch (Exception e) {

                }

                break;
            default:
        }
        return ret;
    }
}
