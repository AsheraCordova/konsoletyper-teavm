/*
 *  Copyright 2024 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.backend.wasm.generate.gc.classes;

import java.util.List;
import java.util.Queue;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.TemporaryVariablePool;
import org.teavm.backend.wasm.generate.gc.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.gc.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.expression.WasmGetLocal;
import org.teavm.backend.wasm.model.expression.WasmReturn;
import org.teavm.model.ValueType;

class WasmGCNewArrayFunctionGenerator {
    private WasmModule module;
    private WasmFunctionTypes functionTypes;
    private WasmGCClassInfoProvider classInfoProvider;
    private WasmFunctionType newArrayFunctionType;
    private WasmGCNameProvider names;
    private Queue<Runnable> queue;

    WasmGCNewArrayFunctionGenerator(WasmModule module, WasmFunctionTypes functionTypes,
            WasmGCClassInfoProvider classInfoProvider, WasmGCNameProvider names,
            Queue<Runnable> queue) {
        this.module = module;
        this.functionTypes = functionTypes;
        this.classInfoProvider = classInfoProvider;
        this.names = names;
        this.queue = queue;
    }

    WasmFunction generateNewArrayFunction(ValueType itemType) {
        var classInfo = classInfoProvider.getClassInfo(ValueType.arrayOf(itemType));
        var functionType = new WasmFunctionType(null, classInfo.getType(), List.of(WasmType.INT32));
        module.types.add(functionType);
        functionType.setFinal(true);
        functionType.getSupertypes().add(getNewArrayFunctionType());
        var function = new WasmFunction(functionType);
        function.setName(names.topLevel("Array<" + names.suggestForType(itemType) + ">@new"));
        module.functions.add(function);

        queue.add(() -> {
            var sizeLocal = new WasmLocal(WasmType.INT32, "length");
            function.add(sizeLocal);
            var tempVars = new TemporaryVariablePool(function);
            var genUtil = new WasmGCGenerationUtil(classInfoProvider, tempVars);
            var targetVar = new WasmLocal(classInfo.getType(), "result");
            function.add(targetVar);
            genUtil.allocateArray(itemType, () -> new WasmGetLocal(sizeLocal), null, targetVar, function.getBody());
            function.getBody().add(new WasmReturn(new WasmGetLocal(targetVar)));
        });
        return function;
    }

    WasmFunctionType getNewArrayFunctionType() {
        if (newArrayFunctionType == null) {
            newArrayFunctionType = functionTypes.of(classInfoProvider.getClassInfo("java.lang.Object").getType(),
                    WasmType.INT32);
            newArrayFunctionType.setFinal(false);
        }
        return newArrayFunctionType;
    }
}
