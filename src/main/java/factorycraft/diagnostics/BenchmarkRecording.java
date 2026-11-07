package factorycraft.diagnostics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;

/** CPU/allocation samples plus vanilla packet events; packet totals exclude setup and recording overhead. */
final class BenchmarkRecording implements AutoCloseable {
    record Summary(long sentPackets,long sentEncodedBytes,long receivedPackets,long receivedEncodedBytes) {}
    private final Recording recording=new Recording();
    private final Path path;
    BenchmarkRecording(Path path) {
        this.path=path;recording.setName("FactoryCraft factory benchmark");
        // Keep the entire finite measurement; a rolling size cap would silently undercount busy clients.
        recording.setMaxSize(0);
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20)).withStackTrace();
        recording.enable("jdk.ObjectAllocationSample").with("throttle","150/s").withStackTrace();
        recording.enable("jdk.GarbageCollection");
        recording.enable("minecraft.PacketSent").withoutStackTrace();
        recording.enable("minecraft.PacketReceived").withoutStackTrace();recording.start();
    }
    Summary finish(Instant start,Instant end) throws IOException {
        recording.stop();recording.dump(path);long sent=0,sentBytes=0,received=0,receivedBytes=0;
        try(var file=new RecordingFile(path)) {
            while(file.hasMoreEvents()) {
                var event=file.readEvent();var name=event.getEventType().getName();
                if(event.getStartTime().isBefore(start) || event.getStartTime().isAfter(end))continue;
                if(name.equals("minecraft.PacketSent")) { sent++;sentBytes+=event.getInt("bytes"); }
                if(name.equals("minecraft.PacketReceived")) { received++;receivedBytes+=event.getInt("bytes"); }
            }
        }
        return new Summary(sent,sentBytes,received,receivedBytes);
    }
    @Override public void close() { recording.close(); }
}
