# divinity

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/build-Gradle-blue.svg)](https://gradle.org/)
[![Platform](https://img.shields.io/badge/platform-cross--platform-lightgrey.svg)]()

A Java decompiler and deobfuscator focused on bytecode that other tools fail on — control-flow flattening, virtualization (Zelix, DashO, custom VMs), MBA expression encryption, string encryption, identifier-mangled code.

Written in Java 21. ~15k lines. MIT licensed.

---

## Why this exists

Mainstream decompilers like CFR, Vineflower, and Procyon do an excellent job on clean bytecode. They were not designed for adversarial input. Heavily protected bytecode — the kind produced by commercial obfuscators or custom in-house tools — defeats them: they emit `goto` spaghetti, leave VM dispatch loops intact, or simply refuse to decompile methods at all.

`divinity` is built around the assumption that the input is adversarial. Every pass is designed to be robust against transformations that a malicious or paranoid obfuscator would apply.

## Architecture

The pipeline is conceptually similar to an optimizing compiler running in reverse, with deobfuscation passes layered between lifting and code emission.

```
class file  ──▶  bytecode  ──▶  CFG  ──▶  SSA  ──▶  deobfuscation passes  ──▶  type inference  ──▶  AST  ──▶  Java source
```

### Core components

| Package | Responsibility |
|---|---|
| `com.divinity.classfile` | Class file parser (constant pool, attributes, code) |
| `com.divinity.bytecode` | Instruction model, basic block construction |
| `com.divinity.cfg` | Control-flow graph, dominator tree, loop nesting, structural analysis |
| `com.divinity.ssa` | SSA construction (dominance frontiers), phi placement, def-use chains |
| `com.divinity.optimizer` | SSA-based optimizations: constant propagation, copy propagation, DCE |
| `com.divinity.deobf.controlflow` | Control-flow unflattening (defeats switch-dispatched obfuscation) |
| `com.divinity.deobf.mba` | Mixed Boolean-Arithmetic expression solver |
| `com.divinity.deobf.vm` | VM devirtualization (Zelix, DashO, generic dispatch detection) |
| `com.divinity.deobf.string` | String decryption via bytecode emulation |
| `com.divinity.deobf.reflection` | Reflection call resolution to direct invocations |
| `com.divinity.deobf.lambda` | Lambda and `invokedynamic` reconstruction |
| `com.divinity.deobf.innerclass` | Inner / anonymous / local class recovery |
| `com.divinity.deobf.patterns` | Obfuscator fingerprinting (ProGuard, Zelix, Allatori, DashO, yGuard) |
| `com.divinity.types` | Constraint-based type inference, generic reconstruction |
| `com.divinity.ast` | High-level AST, structuring of loops and conditionals |
| `com.divinity.writer` | Java source emission |

See [TECHNICAL_SUMMARY.md](TECHNICAL_SUMMARY.md) for an in-depth description of each pass and the algorithms they use.

## Build

```bash
gradle fatJar
```

Produces `build/libs/divinity-5.0.0-all.jar` — a single fat jar with all dependencies bundled.

Requires JDK 21+.

## Usage

```bash
java -jar divinity-5.0.0-all.jar <input.jar|input.class> -o <output-dir>
```

Common flags:

```
--aggressive            Enable all deobfuscation passes (slower, best for protected bytecode)
--ssa                   Run SSA-based optimizations
--unflatten             Control-flow unflattening
--solve-mba             MBA expression solving
--decrypt-strings       String literal decryption via emulation
--devirtualize          VM devirtualization
--resolve-reflection    Convert reflection calls to direct invocations
--threads N             Worker threads (default: number of cores)
```

## Testing

`testfiles/` contains sample inputs used during development — small handwritten classes (`Calculator.java`, `TestClass.java`) compiled and used to exercise the pipeline end-to-end.

## What this does NOT do

- It is not a replacement for Vineflower or CFR on clean code — those tools are more polished and faster for that case.
- It does not (yet) handle native methods, JNI bindings, or bytecode produced by non-`javac` compilers (Kotlin, Scala) particularly well.
- It does not bypass DRM, anti-tamper, or runtime integrity checks. Those are out of scope.

## Related projects

- [`Xprotect-obfuscator`](https://github.com/xwezent/Xprotect-obfuscator) — companion bytecode obfuscator, written as a research counterpart. Every protection it implements is something `divinity` has to defeat.

## License

MIT — see [LICENSE](LICENSE).

## Status

Research / personal project. Issues and pull requests welcome.
