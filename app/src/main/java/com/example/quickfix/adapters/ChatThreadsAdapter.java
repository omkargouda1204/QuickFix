package com.example.quickfix.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.ChatThread;
import com.example.quickfix.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatThreadsAdapter extends RecyclerView.Adapter<ChatThreadsAdapter.VH> {

    public interface ThreadClickListener {
        void onThreadClick(ChatThread thread);
    }

    private final List<ChatThread> items = new ArrayList<>();
    private final ThreadClickListener listener;
    private final SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat outFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public ChatThreadsAdapter(ThreadClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ChatThread> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_thread, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChatThread t = items.get(position);
        String name = safe(t.otherPartyName).isEmpty() ? "Chat" : t.otherPartyName;
        h.tvName.setText(name);
        if (h.tvAvatar != null) {
            h.tvAvatar.setText(name.substring(0, 1).toUpperCase(Locale.getDefault()));
        }
        h.tvLastMessage.setText(safe(t.lastMessage).isEmpty() ? "No messages yet" : t.lastMessage);
        h.tvTime.setText(formatTime(t.lastMessageAt));
        if (t.unreadCount > 0) {
            h.tvUnread.setVisibility(View.VISIBLE);
        } else {
            h.tvUnread.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onThreadClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTime(String raw) {
        String s = safe(raw);
        if (s.isEmpty()) return "";
        try {
            Date d = inFmt.parse(s.length() >= 19 ? s.substring(0, 19) : s);
            return d == null ? "" : outFmt.format(d);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar;
        TextView tvName;
        TextView tvLastMessage;
        TextView tvTime;
        TextView tvUnread;

        VH(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvThreadAvatar);
            tvName = itemView.findViewById(R.id.tvThreadName);
            tvLastMessage = itemView.findViewById(R.id.tvThreadLastMessage);
            tvTime = itemView.findViewById(R.id.tvThreadTime);
            tvUnread = itemView.findViewById(R.id.tvThreadUnread);
        }
    }
}

