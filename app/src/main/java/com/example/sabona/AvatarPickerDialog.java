package com.example.sabona;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog koji prikazuje mrežu avatara (Android drawable resursi).
 * Pozivalac treba da implementira OnAvatarSelectedListener.
 */
public class AvatarPickerDialog extends DialogFragment {

    public interface OnAvatarSelectedListener {
        void onAvatarSelected(String resName, int resId);
    }

    private OnAvatarSelectedListener listener;

    // Lista parova: (drawable resource name, drawable resource id)
    private static final int[] AVATAR_IDS = {
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass,
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_search,
            android.R.drawable.ic_menu_share,
            android.R.drawable.ic_menu_send,
            android.R.drawable.ic_menu_add,
            android.R.drawable.ic_menu_info_details,
            android.R.drawable.ic_menu_edit,
            android.R.drawable.ic_menu_view
    };

    private static final String[] AVATAR_NAMES = {
            "gallery", "places", "compass", "camera",
            "manage", "search", "share", "send",
            "add", "info", "edit", "view"
    };

    public static AvatarPickerDialog newInstance() {
        return new AvatarPickerDialog();
    }

    public void setOnAvatarSelectedListener(OnAvatarSelectedListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle("Odaberi avatar");
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        rv.setPadding(16, 16, 16, 16);

        List<int[]> items = new ArrayList<>();
        for (int i = 0; i < AVATAR_IDS.length; i++) {
            items.add(new int[]{AVATAR_IDS[i], i});
        }

        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView iv = new ImageView(parent.getContext());
                int size = (int) (72 * parent.getContext().getResources().getDisplayMetrics().density);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
                lp.setMargins(8, 8, 8, 8);
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iv.setPadding(8, 8, 8, 8);
                return new RecyclerView.ViewHolder(iv) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageView iv = (ImageView) holder.itemView;
                iv.setImageResource(AVATAR_IDS[position]);
                iv.setBackgroundResource(android.R.drawable.btn_default);
                iv.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAvatarSelected(AVATAR_NAMES[position], AVATAR_IDS[position]);
                    }
                    dismiss();
                });
            }

            @Override
            public int getItemCount() { return AVATAR_IDS.length; }
        });

        return rv;
    }
}
