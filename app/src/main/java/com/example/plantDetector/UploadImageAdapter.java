package com.example.plantDetector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.List;

public class UploadImageAdapter extends RecyclerView.Adapter<UploadImageAdapter.UploadImageViewHolder>  {
    private final Context mContext;
    private final List<Upload> mUploads;

    interface OnStateClickListenerUpload {
        void onClickAdd(Upload upload) throws IOException;
    }

    interface OnStateClickListenerDelete {
        void onClickAdd(Upload upload, int position) throws IOException;
    }
    private final OnStateClickListenerUpload onStateClickListenerUpload;
    private final OnStateClickListenerDelete onStateClickListenerDelete;

    public UploadImageAdapter(Context context, List<Upload> uploads,
                              OnStateClickListenerUpload onStateClickListenerUpload,
                              OnStateClickListenerDelete onStateClickListenerDelete
    ) {
        mContext = context;
        mUploads = uploads;
        this.onStateClickListenerUpload = onStateClickListenerUpload;
        this.onStateClickListenerDelete = onStateClickListenerDelete;
    }

    @NonNull
    @Override
    public UploadImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(mContext).inflate(R.layout.upload_image_block, parent, false);
        return new UploadImageViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    public void onBindViewHolder(@NonNull UploadImageAdapter.UploadImageViewHolder holder, int position) {
        Upload uploadCurrent = mUploads.get(position);
        holder.textViewName.setText("Изображение " + (mUploads.size()-position));

        Glide.with(mContext.getApplicationContext())
                .load(uploadCurrent.getImageUrl())
                .centerCrop()
                .into(holder.imageView);

        holder.textViewName.setOnClickListener(v -> {
            try {
                onStateClickListenerUpload.onClickAdd(uploadCurrent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        holder.imageView.setOnClickListener(v -> {
            try {
                onStateClickListenerUpload.onClickAdd(uploadCurrent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            try {
                onStateClickListenerDelete.onClickAdd(uploadCurrent, position);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mUploads.size();
    }

    public static class UploadImageViewHolder extends RecyclerView.ViewHolder {
        public TextView textViewName;
        public ImageView imageView;
        public Button deleteButton;
        public UploadImageViewHolder(View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_image_name);
            imageView = itemView.findViewById(R.id.image_view_small);
            deleteButton = itemView.findViewById(R.id.button_delete_image);
        }
    }
}
