package org.wanshu.reader.data.export;

import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** File I/O stays off the serial personal-DB queue. Only the middle step accesses Room. */
public final class PersonalDataTransferTask implements Runnable {
    private final PersonalDataTransfer transfer;
    private final Uri uri;
    private final boolean importing;
    private int step;
    private String json;

    public PersonalDataTransferTask(PersonalDataTransfer transfer, Uri uri, boolean importing) {
        this.transfer = transfer; this.uri = uri; this.importing = importing;
    }
    @Override public void run() {
        try {
            if (step == 0) {
                if (importing) json = read();
                step = 1; transfer.executors.getPersonalDbExecutor().execute(this);
            } else if (step == 1) {
                if (importing) {
                    int count = PersonalDataImporter.importFromJson(transfer.database, json);
                    transfer.finish("已合并 " + count + " 项个人数据；未导入正文", null);
                } else {
                    json = PersonalDataExporter.exportToJson(transfer.database);
                    step = 2; transfer.executors.getCpuExecutor().execute(this);
                }
            } else {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > PersonalDataImporter.MAX_BYTES) throw new IOException("备份超过 4 MiB");
                try (OutputStream stream = transfer.context.getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new IOException("无法写入所选文件");
                    stream.write(bytes); stream.flush();
                }
                transfer.finish("个人数据备份已保存（不含小说正文）", null);
            }
        } catch (Exception failure) {
            String detail = failure.getMessage();
            if (detail == null) detail = "文件格式或存储错误";
            if (detail.length() > 160) detail = detail.substring(0, 160);
            transfer.finish((importing ? "导入失败，未提交备份：" : "导出失败，目标文件可能不完整：") + detail, failure);
        }
    }
    private String read() throws IOException {
        try (InputStream stream = transfer.context.getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("无法读取所选文件");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > PersonalDataImporter.MAX_BYTES) throw new IOException("备份超过 4 MiB");
                bytes.write(buffer, 0, count);
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        }
    }
}
