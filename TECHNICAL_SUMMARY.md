# divinity — Technical Summary

This document describes the algorithms and design choices behind `divinity`'s deobfuscation and decompilation pipeline. It is intended as a companion to [README.md](README.md), which gives the high-level overview.

Nothing here is benchmarked against commercial tools. The point of this document is to explain *how* each pass works, not to make claims about *how well* it works relative to alternatives.

---

## Pipeline

```
  class file
      │
      ▼
  ┌─────────────────┐
  │ classfile parse │   constant pool, attributes, code attribute
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │  basic blocks   │   instruction stream → BBs split on branch targets
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │       CFG       │   edges, dominators, dominance frontiers, loops
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │       SSA       │   Cytron et al. — phi placement, renaming
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │  optimizations  │   const prop, copy prop, DCE on SSA
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │  deobfuscation  │   unflatten · MBA · VM devirt · string decrypt · reflection
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │ type inference  │   constraint generation, unification
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │   structuring   │   reconstruct loops, conditionals, switches
  └────────┬────────┘
           ▼
  ┌─────────────────┐
  │  source emit    │   Java source from AST
  └─────────────────┘
```

Each stage operates on the IR produced by the previous one. Passes inside the deobfuscation stage are individually toggleable via CLI flags so behavior can be inspected one transformation at a time.

---

## SSA construction

Follows the classic Cytron–Ferrante–Rosen–Wegman–Zadeck algorithm:

1. Compute the dominator tree from the CFG.
2. Compute dominance frontiers per block.
3. Insert φ nodes at the dominance frontier of every definition.
4. Rename variables in a pre-order traversal of the dominator tree, maintaining per-variable definition stacks.

```
for each block B in CFG:
    if |preds(B)| >= 2:
        for each pred P of B:
            runner = P
            while not runner.strictlyDominates(B):
                runner.dominanceFrontier.add(B)
                runner = runner.immediateDominator
```

SSA is the foundation for the optimization passes downstream — without it, constant propagation and DCE become harder and less precise on JVM bytecode (which has explicit stack and local-variable slots that alias each other heavily after obfuscation).

### SSA-driven optimizations

- **Constant propagation** — folds chains of `iconst → istore → iload → iadd → istore → iload` into a single SSA value.
- **Copy propagation** — eliminates copies introduced by stack-to-local transfers.
- **Dead code elimination** — removes SSA values with no live uses (mark-and-sweep over the use-def graph).

Example:

```
bytecode:                  SSA after opt:
  ICONST_1                   x_1 = 3
  ISTORE 1                   return x_1
  ILOAD 1
  ICONST_2
  IADD
  ISTORE 1
  ILOAD 1
  IRETURN
```

---

## Control-flow unflattening

Flattening rewrites a method's CFG into a single dispatch loop with a state variable. Each original basic block becomes a `case` of a `switch`, and the state variable encodes the original control flow:

```java
int state = 0;
while (true) {
    switch (state) {
        case 0: doA(); state = 5; break;
        case 5: doB(); state = 2; break;
        case 2: doC(); return;
    }
}
```

The unflattener works in three phases:

1. **Detect the dispatcher.** A flattened method has a single back-edge loop containing a `tableswitch` or `lookupswitch` whose key is read from a single local. That local is the *state variable*. The `switch` is the *dispatcher*.
2. **Symbolically execute each case.** For every case body, track concrete writes to the state variable. The terminal write tells us where control transfers next.
3. **Rewrite the CFG.** Replace the dispatch loop with direct edges between cases based on the symbolic-execution result. The state variable becomes dead and is eliminated by DCE.

False positives (a non-flattened method that *happens* to contain a switch on a local) are rejected by a sanity check: a real flattening always produces a CFG where the dispatcher dominates every case and is the back-edge target.

---

## MBA (Mixed Boolean-Arithmetic) simplification

MBA encodes a simple expression like `x + y` as a more complex equivalent using identities that mix bitwise and arithmetic operators. Common patterns:

```
(x ^ y) + 2*(x & y)      ≡  x + y
(x | y) - (x & y)        ≡  x ^ y
~(~x & ~y)               ≡  x | y
-(~x)                    ≡  x + 1
```

`divinity` recognizes a fixed library of these identities and applies them as rewrite rules over the SSA. For patterns not in the library, the solver can be configured to invoke an external SMT backend (Z3) to check semantic equivalence against a candidate simpler form:

```
(set-logic QF_BV)
(declare-const x (_ BitVec 32))
(declare-const y (_ BitVec 32))
(assert (not (= (bvadd (bvxor x y) (bvmul #x00000002 (bvand x y)))
                (bvadd x y))))
(check-sat)   ; expect 'unsat' → expressions are equivalent
```

The SMT-based path is slower and is only used when the pattern library doesn't match.

---

## VM devirtualization

Some obfuscators (Zelix KlassMaster, DashO, custom in-house tools) replace bytecode with a custom virtual machine: original instructions are stored as an opaque byte array and an interpreter loop dispatches them at runtime.

The devirtualizer:

1. **Locates the dispatch loop** by structural pattern — an unbounded loop containing a switch on a value loaded from an instruction-pointer-like local, with array accesses indexed by that local.
2. **Recovers the opcode table** — maps each VM opcode to the JVM instruction it emulates. For well-known VMs this is a fixed mapping. For unknown VMs, the table is recovered by symbolically executing each case of the dispatch switch and observing its effect on the VM's simulated stack and registers.
3. **Translates the byte array back to JVM bytecode** using the recovered table.
4. **Replaces the dispatch loop** with the translated bytecode in the SSA IR.

The devirtualizer is the most fragile pass in the pipeline because it depends on correctly identifying the VM's structure. When detection fails it leaves the method untouched rather than producing wrong output.

---

## String decryption

Obfuscators commonly replace string literals with a call to a decryption routine that takes an encrypted byte array (or a key + ciphertext) and returns the decrypted string at runtime.

Rather than re-implement every decryption scheme, `divinity` runs an in-process bytecode emulator on the decryption method and captures its return value:

1. Identify the decryption method by its signature — a static method taking primitive or `byte[]` arguments and returning `String`, called immediately on a constant input at the use site.
2. Emulate the call with the constant input as the actual arguments.
3. Substitute the emulator's return value as a string-constant literal in the IR.

The emulator is sandboxed — no file I/O, no network, no reflection escape — and has a step budget to prevent malicious decryption methods from hanging the decompiler.

---

## Type inference

JVM local variables and stack slots are typed only loosely (primitive-or-reference, plus a width). Bytecode produced by Java compilers can be re-typed by reading the local-variable table, but obfuscators routinely strip that table.

The type inference engine generates equality and subtype constraints between SSA values from each instruction's operand types, then unifies them:

```
IADD operands and result:  τ_a = int, τ_b = int, τ_result = int
CHECKCAST T:               τ_in ⊑ Object, τ_out = T
ALOAD x → ASTORE y:        τ_x = τ_y
INVOKEVIRTUAL m:           τ_receiver ⊑ declaring class of m
                           τ_arg_i ⊑ param_i of m
                           τ_result = return type of m
```

Unification produces the most specific type compatible with all constraints. Where multiple types are possible (e.g. an Object that's only used through `Object` methods), the most general type is kept.

---

## Reflection resolution

Reflection-based dispatch — `Method.invoke`, `Class.forName(...).getDeclaredMethod(...).invoke(...)` — is reduced to a direct call when both the target class name and the method signature are constants that survive SSA constant propagation.

This catches the common obfuscator trick of hiding direct method calls behind reflection. It does *not* attempt to resolve reflection where the target name is computed from runtime input — that's genuinely dynamic dispatch and rewriting it would be wrong.

---

## Lambda and `invokedynamic` reconstruction

Java lambdas compile to `invokedynamic` with `LambdaMetafactory` as the bootstrap method. The bootstrap arguments contain the captured method handle (the synthetic `lambda$N` method) and the functional-interface descriptor. Both are constants in the constant pool.

The reconstructor walks every `invokedynamic` whose bootstrap is `LambdaMetafactory`, recovers the captured method, and emits the lambda or method-reference syntax in the AST.

---

## Pattern recognition

A fingerprinting pass tags methods with the likely obfuscator that processed them, based on structural patterns:

| Obfuscator | Tells |
|---|---|
| ProGuard | Short identifiers (`a`, `b`, `aa`, `ab`), unchanged control flow, no string encryption |
| Zelix KlassMaster | Heavy XOR usage on `char[]` arrays for strings, dispatch loops, reflection trampolines |
| Allatori | `invokedynamic`-based string concat, switch-flattened control flow, watermarked constant pool |
| DashO | Distinctive VM opcode set, stack-manipulation patterns, integer-string-pool encoding |
| yGuard | Name obfuscation, occasional basic flow obfuscation, no string encryption |

The tag is used downstream to bias subsequent passes — e.g. enabling the Zelix-specific string-decrypt path only on methods tagged as Zelix-processed.

---

## Limitations

- **Native methods** are left as-is. `divinity` doesn't disassemble native code.
- **Kotlin / Scala bytecode** works but with caveats: language-specific synthetic methods, generated `WhenMappings`, and metadata annotations aren't reconstructed back to source-level constructs.
- **DEX / Android bytecode** is not supported.
- **DRM / runtime integrity checks** are out of scope. The decompiler does not patch or bypass them.
- **Detection-evasion is not goal.** When a pass can't be confident, it leaves the input untouched rather than risk emitting wrong code.

---

## References

- Cytron, R., Ferrante, J., Rosen, B. K., Wegman, M. N., & Zadeck, F. K. (1991). *Efficiently computing static single assignment form and the control dependence graph.* TOPLAS 13(4).
- Ugarte-Pedrero, X. et al. (2015). *SoK: Deep packer inspection.* IEEE S&P.
- Yadegari, B. & Debray, S. (2015). *Symbolic execution of obfuscated code.* CCS.
- de Moura, L. & Bjørner, N. (2008). *Z3: An efficient SMT solver.* TACAS.
