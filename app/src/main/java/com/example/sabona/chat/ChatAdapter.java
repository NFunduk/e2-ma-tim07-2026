package com.example.sabona.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.R;
import com.example.sabona.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Prikazuje poruke iz čet sobe — poruke drugih igrača sa leve strane,
 * poruke ulogovanog igrača sa desne strane (8.d).
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String currentUid;

    public ChatAdapter(String currentUid) {
        this.currentUid = currentUid;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        boolean isMine = currentUid != null && currentUid.equals(message.getSenderId());
        return isMine ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            return new SentViewHolder(inflater.inflate(R.layout.item_chat_sent, parent, false));
        } else {
            return new ReceivedViewHolder(inflater.inflate(R.layout.item_chat_received, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).bind(message);
        } else if (holder instanceof ReceivedViewHolder) {
            ((ReceivedViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvText;
        private final TextView tvTime;

        SentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvChatText);
            tvTime = itemView.findViewById(R.id.tvChatTime);
        }

        void bind(ChatMessage message) {
            tvText.setText(message.getText());
            tvTime.setText(message.getTimeText());
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSender;
        private final TextView tvText;
        private final TextView tvTime;

        ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvChatSender);
            tvText = itemView.findViewById(R.id.tvChatText);
            tvTime = itemView.findViewById(R.id.tvChatTime);
        }

        void bind(ChatMessage message) {
            tvSender.setText(message.getSenderName());
            tvText.setText(message.getText());
            tvTime.setText(message.getTimeText());
        }
    }
}