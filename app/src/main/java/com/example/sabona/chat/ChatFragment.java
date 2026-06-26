package com.example.sabona.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sabona.daily.DailyMissionRepository;

import com.example.sabona.R;
import com.example.sabona.utils.PresenceManager;
import com.google.firebase.auth.FirebaseAuth;

public class ChatFragment extends Fragment {

    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView tvChatRegion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        recyclerView = view.findViewById(R.id.chatRecycler);
        etMessage = view.findViewById(R.id.etChatMessage);
        btnSend = view.findViewById(R.id.btnChatSend);
        tvChatRegion = view.findViewById(R.id.tvChatRegion);

        View chatRoot = view.findViewById(R.id.chatRoot);
        ViewCompat.setOnApplyWindowInsetsListener(chatRoot, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeBottom);

            if (getActivity() instanceof com.example.sabona.MainActivity) {
                ((com.example.sabona.MainActivity) getActivity()).setBottomNavVisible(!imeVisible);
            }

            if (adapter != null && adapter.getItemCount() > 0) {
                recyclerView.post(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));
            }
            return insets;
        });

        adapter = new ChatAdapter(currentUid);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        viewModel.getRegion().observe(getViewLifecycleOwner(), region -> {
            if (region != null) {
                tvChatRegion.setText("Čet · region " + region);
            }
        });

        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.setMessages(messages);
            if (messages != null && !messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && isAdded()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString();
            if (text.trim().isEmpty()) return;

            viewModel.sendMessage(text);
            etMessage.setText("");

            new DailyMissionRepository()
                    .completeSendChat(currentUid, null);
        });

        viewModel.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        PresenceManager.setInChat(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        PresenceManager.setInChat(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        PresenceManager.setInChat(false);
        if (getActivity() instanceof com.example.sabona.MainActivity) {
            ((com.example.sabona.MainActivity) getActivity()).setBottomNavVisible(true);
        }
    }
}
