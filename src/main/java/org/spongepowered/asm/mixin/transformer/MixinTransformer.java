/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.asm.mixin.transformer;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.throwables.MixinException;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;
import org.spongepowered.asm.transformers.TreeTransformer;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Constants;

/**
 * Transformer which manages the mixin configuration and application process
 */
class MixinTransformer extends TreeTransformer implements IMixinTransformer {
    
    /**
     * Transformer extensions
     */
    private final Extensions extensions;

    /**
     * Mixin processor which actually manages application of mixins
     */
    private final MixinProcessor processor;
    
    /**
     * Class generator 
     */
    private final MixinClassGenerator generator;

    /**
     * Synthetic class registry
     */
    private final SyntheticClassRegistry syntheticClassRegistry;

    public MixinTransformer() {
        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
        
        Object globalMixinTransformer = environment.getActiveTransformer();
        if (globalMixinTransformer instanceof IMixinTransformer) {
            throw new MixinException("Terminating MixinTransformer instance " + this);
        }
        
        // I am a leaf on the wind
        environment.setActiveTransformer(this);
        
        this.syntheticClassRegistry = new SyntheticClassRegistry();
        this.extensions = new Extensions(this.syntheticClassRegistry);

        this.processor = new MixinProcessor(environment, this.extensions);
        this.generator = new MixinClassGenerator(environment, this.extensions);
        
        DefaultExtensions.create(environment, this.extensions, this.syntheticClassRegistry);
    }
    
    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.IMixinTransformer
     *      #getExtensions()
     */
    @Override
    public IExtensionRegistry getExtensions() {
        return this.extensions;
    }
    
    /* (non-Javadoc)
     * @see org.spongepowered.asm.service.ITransformer#getName()
     */
    @Override
    public String getName() {
        return this.getClass().getName();
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.service.ITransformer#isDelegationExcluded()
     */
    @Override
    public boolean isDelegationExcluded() {
        return true;
    }

    /**
     * Run audit process on current mixin processor
     * 
     * @param environment Environment for audit
     */
    @Override
    public void audit(MixinEnvironment environment) {
        this.processor.audit(environment);
    }

    /**
     * Callback from hotswap agent
     * 
     * @param mixinClass Name of the mixin
     * @param classNode New class
     * @return List of classes that need to be updated
     */
    @Override
    public List<String> reload(String mixinClass, ClassNode classNode) {
        return this.processor.reload(mixinClass, classNode);
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.IMixinTransformer
     *      #transformClassBytes(java.lang.String, java.lang.String, byte[])
     */
    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
        if (transformedName == null) {
            return basicClass;
        }
        
        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();

        if (basicClass == null) {
            return this.generateClass(environment, transformedName);
        }
        
        return this.transformClass(environment, transformedName, basicClass);
    }

    /**
     * Apply mixins and postprocessors to the supplied class
     * 
     * @param environment Current environment
     * @param name Class transformed name
     * @param classBytes Class bytecode
     * @return Transformed bytecode
     */
    public byte[] transformClass(MixinEnvironment environment, String name, byte[] classBytes) {
        ClassNode classNode = this.readClass(classBytes);
        if (this.processor.applyMixins(environment, name, classNode)) {
            return this.writeClass(classNode);
        }
        return classBytes;
    }

    /**
     * Apply mixins and postprocessors to the supplied class
     * 
     * @param environment Current environment
     * @param name Class transformed name
     * @param classNode Class tree
     * @return true if the class was transformed
     */
    public boolean transformClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return this.processor.applyMixins(environment, name, classNode);
    }
    
    /**
     * Generate the specified mixin-synthetic class
     * 
     * @param environment Current environment
     * @param name Class name to generate
     * @return Generated bytecode or <tt>null</tt> if no class was generated
     */
    public byte[] generateClass(MixinEnvironment environment, String name) {
        ClassNode classNode = MixinTransformer.createEmptyClass(name);
        if (this.generator.generateClass(environment, name, classNode)) {
            return this.writeClass(classNode);
        }
        return null;
    }
    
    /**
     * @param environment Current environment
     * @param name Class transformed name
     * @param classNode Empty classnode to populate
     * @return True if the class was generated successfully
     */
    public boolean generateClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return this.generator.generateClass(environment, name, classNode);
    }
    
    /**
     * You need to ask yourself why you're reading this comment  
     */
    private static ClassNode createEmptyClass(String name) {
        ClassNode classNode = new ClassNode(Bytecode.ASM_API_VERSION);
        classNode.name = name.replace('.', '/');
        classNode.version = MixinEnvironment.getCompatibilityLevel().classVersion();
        classNode.superName = Constants.OBJECT;
        return classNode;
    }

}
