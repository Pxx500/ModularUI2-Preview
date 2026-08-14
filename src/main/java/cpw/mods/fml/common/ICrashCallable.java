package cpw.mods.fml.common;

/** Minimal Forge crash-report callback contract used by the preview runtime. */
public interface ICrashCallable {

    String call() throws Exception;

    String getLabel();
}
