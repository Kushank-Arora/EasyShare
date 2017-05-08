package arora.kushank.easyshare;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class FillName extends AppCompatActivity {

    private static final String FILENAME = "easyShare";
    private static final String ACC_NAME = "name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fill_name);
        Button finish= (Button) findViewById(R.id.bFinish);
        final EditText et= (EditText) findViewById(R.id.etName);
        final SharedPreferences sharedData=getSharedPreferences(FILENAME,MODE_PRIVATE);

        assert finish != null;
        finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                assert et != null;
                String name=et.getText().toString();
                SharedPreferences.Editor editor = sharedData.edit();
                editor.putString(ACC_NAME,name);
                editor.commit();
                startActivity(new Intent(FillName.this,SendOrRecieve.class));
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}
