package arora.kushank.easyshare;

import android.Manifest;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class SendOrRecieve extends AppCompatActivity {

    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1024;
    private static final int RESULT_LOAD_IMAGE = 1025;
    private static final String FILE_ADDRESS = "file_add";
    private static final String TAG = "SendOrRevieve";

    Button bSend, bReceive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_or_recieve);
        bSend = (Button) findViewById(R.id.bSendFile);
        bReceive = (Button) findViewById(R.id.bReceiveFile);
        setButtonsActive(false);
        RequestPermission();
        bSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setType("*/*");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                }
                //Intent i2 = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Files.getContentUri());
                startActivityForResult(i, RESULT_LOAD_IMAGE);
            }
        });
        bReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SendOrRecieve.this, ReceiveActivity.class));
            }
        });
    }

    void RequestPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                ) {
            Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
            //ask for permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
            }
        } else {
            setButtonsActive(true);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_CODE) {
            setButtonsActive(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            //Uri selectedImage =
            ClipData clipData = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                clipData = data.getClipData();
            }
            ArrayList<String> filenames = new ArrayList<>();
            if (clipData == null) {
                String filename = data.getData().getPath();
                filename=modifyIfInvalid(this,filename,data.getData());
                filenames.add(filename);
            } else {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    String filename = clipData.getItemAt(i).getUri().getPath();
                    filename=modifyIfInvalid(this,filename,clipData.getItemAt(i).getUri());
                    filenames.add(filename);
                }
            }
            //String picturePath =
            Bundle bag = new Bundle();
            bag.putStringArrayList(FILE_ADDRESS, filenames);
            //bag.putString(FILE_ADDRESS,picturePath);
            Intent intent = new Intent(SendOrRecieve.this, SendActivity.class);
            for (int i = 0; i < filenames.size(); i++) {
                Toast.makeText(this, filenames.get(i), Toast.LENGTH_SHORT).show();
                Log.d(TAG, filenames.get(i));
            }
            intent.putExtras(bag);
            startActivity(intent);
        }
    }

    public static String modifyIfInvalid(Context context, String filename, Uri dataURI) {
        boolean gotError = false;
        try {
            File myFile = new File(filename);
            Log.d(TAG,myFile.length()+"");
            if (myFile.length() == 0)
                gotError = true;

            FileInputStream fis=new FileInputStream(filename);
            fis.close();
        } catch (Exception ex) {
            gotError = true;
        }

        if (gotError) {
            Log.d(TAG,"gotError");
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = context.getContentResolver().query(dataURI, filePathColumn, null, null, null);
            Log.d(TAG,"Trying to read it as image");
            if(cursor==null) {
                filePathColumn[0]=MediaStore.Video.Media.DATA;
                cursor = context.getContentResolver().query(dataURI, filePathColumn, null, null, null);
                Log.d(TAG,"Trying to read it as video");
            }
            if(cursor==null){
                filePathColumn[0]=MediaStore.Audio.Media.DATA;
                cursor = context.getContentResolver().query(dataURI, filePathColumn, null, null, null);
                Log.d(TAG,"Trying to read it as audio");
            }
            if(cursor==null){
                filePathColumn[0]=MediaStore.Files.FileColumns.DATA;
                cursor = context.getContentResolver().query(dataURI, filePathColumn, null, null, null);
                Log.d(TAG,"Trying to read it as file");
            }
            if (cursor != null) {
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                filename = cursor.getString(columnIndex);
                cursor.close();
            }
        }
        return filename;
    }

    public void setButtonsActive(boolean active) {
        bSend.setEnabled(active);
        bReceive.setEnabled(active);
    }
}

