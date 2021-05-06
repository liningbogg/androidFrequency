package com.ningbo.frequency;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.LimitLine.LimitLabelPosition;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.utils.ColorTemplate;
import android.widget.TextView;



import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private boolean isRecording = true;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
            ,Manifest.permission.RECORD_AUDIO};
    private LineChart chart;
    //请求状态码
    private static int REQUEST_PERMISSION_CODE = 1;
    //新的handler类要声明成静态类
    static class MyHandler extends Handler {
        WeakReference<MainActivity> mactivity;

        //构造函数，传来的是外部类的this
        public MyHandler(@NonNull Looper looper, MainActivity activity) {
            super(looper);//调用父类的显式指明的构造函数
            mactivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            MainActivity nactivity = mactivity.get();
            if (nactivity == null)
                return;//avtivity都没了还处理个XXX

            switch (msg.what) {
                case 0:
                    //在这里通过nactivity引用外部类
                    break;
                case 123:
                    double[] power = (double[])msg.obj;
                    //此处更新echarts
                    //nactivity.chart.setBackgroundColor(Color.BLUE);
                    nactivity.setData(msg.arg1, power, 44100);
                    Log.d("hello",msg.arg1+" ");
                    break;
                default:
                    break;
            }
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // // Chart Style // //
        chart = findViewById(R.id.chart_v_LineChart);
        // background color
        chart.setBackgroundColor(Color.WHITE);
        // disable description text
        chart.getDescription().setEnabled(false);
        // enable touch gestures
        chart.setTouchEnabled(true);

        chart.setDrawGridBackground(false);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis;
        {   // // X-Axis Style // //
            xAxis = chart.getXAxis();

            // vertical grid lines
            xAxis.enableGridDashedLine(100f, 10f, 0f);
        }
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMaximum(60f);// Y轴最大值
        leftAxis.setStartAtZero(true);// true:不管最小值是多少都强制这个轴从0开始


        YAxis rightAxis = chart.getAxisRight();
        //rightAxis.setAxisMaxValue(60f);// Y轴最大值
        rightAxis.setAxisMaximum(60f);// Y轴最大值

        rightAxis.setStartAtZero(true);// true:不管最小值是多少都强制这个轴从0开始



        Handler handler=new MyHandler(Looper.myLooper(),this){

        };

        ExecutorService threadPool = Executors.newCachedThreadPool();
        threadPool.execute(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void run(){
                int bufferSize=0;//最小缓冲区大小
                int sampleRateInHz = 44100;//采样率
                int channelConfig = AudioFormat.CHANNEL_IN_MONO; //单声道
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //量化位数
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, REQUEST_PERMISSION_CODE);
                    }
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, REQUEST_PERMISSION_CODE);
                    }
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, REQUEST_PERMISSION_CODE);
                    }
                }
                bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,channelConfig, audioFormat);//计算最小缓冲区
                if(bufferSize<4410){
                    bufferSize = 4410;
                }
                try {
                    AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, bufferSize);//创建AudioRecorder对象
                    short[] buffer = new short[bufferSize];
                    double[] doubleFFT = new double[bufferSize];
                    double[] power = new double[doubleFFT.length / 2];
                    audioRecord.startRecording();//开始录音
                    int r = 0;
                    DoubleFFT_1D doubleFFT_1d = new DoubleFFT_1D(bufferSize);
                    while (isRecording) {
                        int bufferReadResult = audioRecord.read(buffer,0,bufferSize);
                        for (int i = 0; i < bufferReadResult; i++)
                        {
                            doubleFFT[i] = (double) buffer[i]/ 32768.0;
                        }
                        //用FFT方法处理从时域转成频域
                        doubleFFT_1d.realForward(doubleFFT);
                        power[0] = Math.abs(doubleFFT[0]);

                        for(int i = 1; i < doubleFFT.length / 2; ++i) {
                            double re  = doubleFFT[2*i];
                            double im  = doubleFFT[2*i+1];
                            double mag = Math.sqrt(re * re + im * im);
                            power[i] = mag;
                        }
                        Message message=new Message();
                        message.what=123;
                        message.obj=power;
                        message.arg1 = doubleFFT.length / 2;
                        handler.sendMessage(message);
                        r++;
                    }
                    audioRecord.stop();//停止录音
                }catch(Exception e){
                    System.out.println(e.toString());
                }

            }
        });
    }
    private void setData(int count, double[] power, int Fs) {

        ArrayList<Entry> values1 = new ArrayList<>();

        for (int i = 0; i < 400; i++) {
            float val = (float) power[i];
            values1.add(new Entry(i*Fs/count*0.5f, val));
        }

        LineDataSet set1;

        if (chart.getData() != null &&
                chart.getData().getDataSetCount() > 0) {
            set1 = (LineDataSet) chart.getData().getDataSetByIndex(0);
            set1.setValues(values1);
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.moveViewToX(chart.getData().getEntryCount());

        } else {
            // create a dataset and give it a type
            set1 = new LineDataSet(values1, "DataSet 1");
            set1.setAxisDependency(AxisDependency.LEFT);
            set1.setColor(ColorTemplate.getHoloBlue());
            //set1.setCircleColor(Color.WHITE);
            set1.setLineWidth(1f);
            set1.setCircleRadius(1f);
            //set1.setFillAlpha(65);
            set1.setFillColor(ColorTemplate.getHoloBlue());
            set1.setHighLightColor(Color.rgb(244, 117, 117));
            set1.setDrawCircleHole(false);

            // create a data object with the data sets
            LineData data = new LineData(set1);
            data.setValueTextColor(Color.WHITE);
            data.setValueTextSize(9f);
            // set data
            chart.setData(data);
        }
    }


}