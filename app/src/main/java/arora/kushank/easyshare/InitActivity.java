package arora.kushank.easyshare;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class InitActivity extends AppCompatActivity {

    private static final String FILENAME = "easyShare";
    private static final String ACC_NAME = "name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init);
        SharedPreferences sharedData=getSharedPreferences(FILENAME,MODE_PRIVATE);
        final String acc_name=sharedData.getString(ACC_NAME, null);
        Thread timer=new Thread(){
            @Override
            public void run() {
                super.run();
                try
                {
                    sleep(2000);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }finally{
                    Intent openSP;
                    if(acc_name == null){
                        openSP = new Intent(InitActivity.this, FillName.class);
                    }else {
                        openSP = new Intent(InitActivity.this, SendOrRecieve.class);
                    }
                    startActivity(openSP);
                }
            }
        };
        timer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}
