package factorycraft.gametest;
/** Archived schema-3 coordinate/all-face-port diagnostics. See validation/assembly-v4-archive.md.
 * Deliberately not a native GameTest: schema-4 tests exercise the new physical layout instead. */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
@interface LegacyAssemblyFixture { String template(); int timeoutTicks() default 100; String batch() default "defaultBatch"; }
