package com.example.plantDetector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class MainActivity extends AppCompatActivity {
    Module module;
    FloatingActionButton camera, gallery;
    ImageView imageView;
    TextView plantTextView, diseaseTextView, placeholderTextView;
    static int imageSize = 256;
    static float [] means = new float[] {0.0f, 0.0f, 0.0f};
    static float [] stds = new float[] {1.0f, 1.0f, 1.0f};

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAMERA_IMAGE_REQUEST = 2;
    private FirebaseStorage mStorage;
    private StorageReference mStorageRef;
    private DatabaseReference mDatabaseRef;
    private ValueEventListener mDBListener;
    private UploadImageAdapter mAdapter;
    UploadImageAdapter.OnStateClickListenerUpload onStateClickListenerUpload;
    UploadImageAdapter.OnStateClickListenerDelete onStateClickListenerDelete;
    private List<Upload> mUploads;
    private Uri mCapturedPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        camera = findViewById(R.id.cameraButton);
        gallery = findViewById(R.id.galleryButton);

        plantTextView = findViewById(R.id.plantName);
        diseaseTextView = findViewById(R.id.diseaseName);
        placeholderTextView = findViewById(R.id.tvPlaceholder);
        imageView = findViewById(R.id.image_view_main);

        try {
            module = LiteModuleLoader.load(assetFilePath(this, "plant-disease-model.ptl"));
        } catch (IOException e) {
            Toast.makeText(this, "Ошибка при загрузке модели", Toast.LENGTH_SHORT).show();
            finish();
        }

        mStorage = FirebaseStorage.getInstance();
        mStorageRef = FirebaseStorage.getInstance().getReference("uploads");
        mDatabaseRef = FirebaseDatabase.getInstance().getReference("uploads");

        camera.setOnClickListener(view -> {
            while (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
            }

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Toast.makeText(this, "Ошибка при создании изображения", Toast.LENGTH_SHORT).show();
                }

                if (photoFile != null) {
                    Uri photoUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".fileprovider", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(takePictureIntent, CAMERA_IMAGE_REQUEST);
                    mCapturedPhotoUri = photoUri;
                }
            }
        });
        gallery.setOnClickListener(view -> {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
        });

        onStateClickListenerUpload = (upload) -> {
            setClassText(upload.getClassId());
            Glide.with(this)
                    .load(upload.getImageUrl())
                    .centerCrop()
                    .into(imageView);
        };

        onStateClickListenerDelete = (upload, position) -> {
            Upload selectedItem = mUploads.get(position);
            final String selectedKey = selectedItem.getKey();

            StorageReference imageRef = mStorage.getReferenceFromUrl(selectedItem.getImageUrl());
            imageRef.delete().addOnSuccessListener(aVoid -> {
                mDatabaseRef.child(selectedKey).removeValue();
                Toast.makeText(getApplicationContext(), "Изображение удалено", Toast.LENGTH_SHORT).show();
            });
        };

        RecyclerView mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setNestedScrollingEnabled(false);

        mUploads = new ArrayList<>();
        mAdapter = new UploadImageAdapter(getApplicationContext(), mUploads, onStateClickListenerUpload, onStateClickListenerDelete);
        mRecyclerView.setAdapter(mAdapter);

        mDBListener = mDatabaseRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mUploads.clear();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    try {
                        String imageUrl = (String) postSnapshot.child("imageUrl").getValue();
                        int classId =  Long.valueOf((long) postSnapshot.child("classId").getValue()).intValue();
                        Upload upload = new Upload(classId, imageUrl);
                        upload.setKey(  postSnapshot.getKey());
                        mUploads.add(upload);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Ошибка при чтении базы данных", Toast.LENGTH_SHORT).show();
                    }
                }
                Collections.reverse(mUploads);
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getApplicationContext(), "Ошибка: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_IMAGE_REQUEST) {
                try {
                    Glide.with(this)
                            .load(mCapturedPhotoUri)
                            .centerCrop()
                            .into(imageView);

                    processImage(mCapturedPhotoUri);
                } catch (IOException e) {
                    Toast.makeText(this, "Ошибка при обработке фотографии", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                try {
                    Uri imageUri = data.getData();
                    Glide.with(this)
                            .load(imageUri)
                            .centerCrop()
                            .into(imageView);

                    processImage(imageUri);
                } catch (IOException e) {
                    Toast.makeText(this, "Ошибка при обработке фотографии", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "Вы не выбрали фото", Toast.LENGTH_SHORT).show();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processImage(Uri imageUri) throws IOException {
        Bitmap image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
        image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);

        int classId = classifyImage(image);
        setClassText(classId);
        uploadImage(imageUri, classId);
    }

    public int classifyImage(Bitmap image) {
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(image, means, stds);
        final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

        final float[] scores = outputTensor.getDataAsFloatArray();

        float maxScore = -Float.MAX_VALUE;
        int maxScoreIdx = -1;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                maxScoreIdx = i;
            }
        }

        return maxScoreIdx;
    }

    private void setClassText(int classId) {
        String plantName = "Растение: " + Classes.PLANT_NAMES[classId];
        String diseaseName;
        if (Classes.DISEASE_NAMES[classId].equals("Здоровый"))  {
            diseaseName = "Здоровый";
            diseaseTextView.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            diseaseName = "Заболевание: " + Classes.DISEASE_NAMES[classId];
            diseaseTextView.setTextColor(getColor(R.color.ltred));
        }

        plantTextView.setText(plantName);
        diseaseTextView.setText(diseaseName);

        plantTextView.setVisibility(View.VISIBLE);
        diseaseTextView.setVisibility(View.VISIBLE);
        placeholderTextView.setVisibility(View.INVISIBLE);
    }

    private void uploadImage(Uri imageUri, int classId) {
        if (imageUri != null) {
            StorageReference fileReference = mStorageRef.child(System.currentTimeMillis()
                    + "." + getFileExtension(imageUri));

            fileReference.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                Toast.makeText(MainActivity.this, "Изображение загружено", Toast.LENGTH_LONG).show();

                                Log.e("VIKA", uri.toString());

                                Upload upload = new Upload(classId, uri.toString());
                                String uploadId = mDatabaseRef.push().getKey();
                                mDatabaseRef.child(uploadId).setValue(upload);
                            })
                            .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, "Изображение не выбрано", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDatabaseRef.removeEventListener(mDBListener);
    }

    private File createImageFile() throws IOException {
        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                "JPEG_" + timeStamp + "_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        );
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cR.getType(uri));
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
