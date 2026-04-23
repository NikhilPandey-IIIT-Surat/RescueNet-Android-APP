package com.example.rescuenet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<Message> messages;
    private final Context       context;

    public MessageAdapter(Context context, List<Message> messages) {
        this.context  = context;
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message msg = messages.get(position);

        holder.tvType.setText(msg.getTypeLabel());
        holder.tvSender.setText(msg.getSenderName() != null ? msg.getSenderName() : "Unknown");
        holder.tvContent.setText(msg.getContent() != null ? msg.getContent() : "");
        holder.tvTime.setText(formatTime(msg.getTimestamp()));
        holder.tvLocation.setText(
                String.format(Locale.US, "%.4f, %.4f", msg.getLatitude(), msg.getLongitude()));
        holder.tvHops.setText("Hops: " + msg.getHopCount());

        // Card color by type
        int bgColor;
        switch (msg.getType()) {
            case SOS:        bgColor = 0xFF3D1A1A; break;
            case MEDICAL:    bgColor = 0xFF2B2A14; break;
            case TRAPPED:    bgColor = 0xFF2B1E10; break;
            case SAFE:       bgColor = 0xFF1A2B1E; break;
            case GOVT_ALERT: bgColor = 0xFF1A1A3D; break;
            default:         bgColor = 0xFF1E2A38; break;
        }
        holder.itemView.setBackgroundColor(bgColor);

        int accentColor;
        switch (msg.getType()) {
            case SOS:        accentColor = 0xFFE74C3C; break;
            case MEDICAL:    accentColor = 0xFFF1C40F; break;
            case TRAPPED:    accentColor = 0xFFE67E22; break;
            case SAFE:       accentColor = 0xFF2ECC71; break;
            case GOVT_ALERT: accentColor = 0xFF3498DB; break;
            default:         accentColor = 0xFF95A5A6; break;
        }
        holder.tvType.setTextColor(accentColor);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvSender, tvContent, tvTime, tvLocation, tvHops;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType     = itemView.findViewById(R.id.tvMsgType);
            tvSender   = itemView.findViewById(R.id.tvMsgSender);
            tvContent  = itemView.findViewById(R.id.tvMsgContent);
            tvTime     = itemView.findViewById(R.id.tvMsgTime);
            tvLocation = itemView.findViewById(R.id.tvMsgLocation);
            tvHops     = itemView.findViewById(R.id.tvMsgHops);
        }
    }
}
