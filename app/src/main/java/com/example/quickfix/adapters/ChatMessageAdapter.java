package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.ChatMessage;
import com.example.quickfix.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.VH> {

    private final Context context;
    private final String currentUserId;
    private final String currentUserRole;
    private final List<ChatMessage> items = new ArrayList<>();
    private final SimpleDateFormat inputFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat outputFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public ChatMessageAdapter(Context context, String currentUserId, String currentUserRole) {
        this.context = context;
        this.currentUserId = currentUserId == null ? "" : currentUserId.trim();
        this.currentUserRole = currentUserRole == null ? "user" : currentUserRole.trim();
    }

    public void setItems(List<ChatMessage> messages) {
        items.clear();
        if (messages != null) items.addAll(messages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_chat_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChatMessage m = items.get(position);
        boolean mine = isMine(m);

        h.leftRoot.setVisibility(mine ? View.GONE : View.VISIBLE);
        h.rightRoot.setVisibility(mine ? View.VISIBLE : View.GONE);

        TextView bubble = mine ? h.rightMessage : h.leftMessage;
        TextView sender = mine ? h.rightSender : h.leftSender;
        TextView time = mine ? h.rightTime : h.leftTime;

        String msgText = safe(m.message);
        bubble.setText(msgText.isEmpty() ? "" : msgText);
        sender.setText(displaySenderType(m));
        time.setText(formatTime(m.createdAt));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private boolean isMine(ChatMessage m) {
        if (m == null) return false;
        String senderType = safe(m.senderType).toLowerCase(Locale.US);
        if (!senderType.isEmpty()) {
            return senderType.equals(currentUserRole.toLowerCase(Locale.US));
        }
        return safe(m.senderId).equals(currentUserId);
    }

    private String displaySenderType(ChatMessage m) {
        String type = safe(m.senderType).toLowerCase(Locale.US);
        if (!type.isEmpty()) return type;
        return isMine(m) ? currentUserRole : ("provider".equalsIgnoreCase(currentUserRole) ? "user" : "provider");
    }

    private String formatTime(String createdAt) {
        String raw = safe(createdAt);
        if (raw.isEmpty()) return "";
        try {
            Date d = inputFmt.parse(raw.length() >= 19 ? raw.substring(0, 19) : raw);
            return d != null ? outputFmt.format(d) : raw;
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    static class VH extends RecyclerView.ViewHolder {
        View leftRoot, rightRoot;
        TextView leftMessage, leftSender, leftTime;
        TextView rightMessage, rightSender, rightTime;

        VH(@NonNull View itemView) {
            super(itemView);
            leftRoot = itemView.findViewById(R.id.leftBubbleRoot);
            rightRoot = itemView.findViewById(R.id.rightBubbleRoot);
            leftMessage = itemView.findViewById(R.id.tvLeftMessage);
            leftSender = itemView.findViewById(R.id.tvLeftSender);
            leftTime = itemView.findViewById(R.id.tvLeftTime);
            rightMessage = itemView.findViewById(R.id.tvRightMessage);
            rightSender = itemView.findViewById(R.id.tvRightSender);
            rightTime = itemView.findViewById(R.id.tvRightTime);
        }
    }
}

