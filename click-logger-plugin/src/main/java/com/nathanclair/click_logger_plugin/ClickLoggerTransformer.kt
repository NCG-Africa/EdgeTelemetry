package com.nathanclair.click_logger_plugin

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationContext
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.Property
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

abstract class ClickLoggerTransformer :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className.startsWith("com/yourapp/") // Replace with your app's package
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean
                    ) {
                        if (
                            owner == "android/widget/Button" &&
                            name == "setOnClickListener" &&
                            descriptor == "(Landroid/view/View\$OnClickListener;)V"
                        ) {
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "com/yourlib/logger/ClickLoggerHelper",
                                "wrap",
                                "(Landroid/view/View\$OnClickListener;)Landroid/view/View\$OnClickListener;",
                                false
                            )
                        }

                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                }
            }
        }
    }
}