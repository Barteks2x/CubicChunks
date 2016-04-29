/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.util;

import com.google.common.base.Throwables;
import cubicchunks.asm.Mappings;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ReflectionUtil {
	public static final <T> T get(Object inObject, Field field, Class<T> fieldType) {
		try {
			return (T) field.get(inObject);
		} catch (IllegalArgumentException | IllegalAccessException ex) {
			throw new RuntimeException(ex);
		} 
	}
	
	public static void set(Object inObject, Field field, Object newValue) {
		try {
			field.set(inObject, newValue);
		} catch (IllegalArgumentException | IllegalAccessException ex) {
			throw new RuntimeException(ex);
		} 
	}
		
	public static final Field findFieldNonStatic(Class<?> inClass, Class<?> type) {
		Field found = null;
		for(Field f : inClass.getDeclaredFields()) {
			if(f.getType().equals(type) && !Modifier.isStatic(f.getModifiers())) {
				if(found != null) {
					throw new RuntimeException("More than one field of type " + type + " found in class " + inClass);
				}
				found = f;
			}
		}
		if(found == null) {
			throw new RuntimeException("Field of type " + type + " not found in class " + inClass);
		}
		return found;
	}

	public static final void removeFinalModifier(Field f) {
		f.setAccessible(true);
		int mod = f.getModifiers();
		mod = mod & ~Modifier.FINAL;
		Field modifiersField = null;
		try {
			modifiersField = Field.class.getDeclaredField("modifiers");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("Field modifiers not found in class Field", e);
		}
		modifiersField.setAccessible(true);
		try {
			modifiersField.setInt(f, mod);
		} catch (IllegalAccessException e) {
			throw new AssertionError("Cannot set field modifiers in class Field", e);
		}
	}

	public static MethodHandle getConstructorMethodHandle(Class<?> visitor, Class<?>...args) {
		try {
			return MethodHandles.lookup().findConstructor(visitor, MethodType.methodType(void.class, args));
		} catch (IllegalAccessException | NoSuchMethodException e) {
			throw Throwables.propagate(e);
		}
	}

	public static String getFieldDescriptor(String name) {
		return "L" + name + ";";
	}

	private static final Field getField(Class<?> owner, Class<?> type, String... possibleNames) {
		Field[] allFields = owner.getDeclaredFields();
		Field foundField = null;
		Set<String> names = new HashSet<>();
		names.addAll(Arrays.asList(possibleNames));

		for(Field field : allFields) {
			if(field.getType() == type && names.contains(field.getName())) {
				foundField = field;
				break;
			}
		}
		foundField.setAccessible(true);
		return foundField;
	}
	public static MethodHandle getFieldGetterHandle(Class<?> owner, Class<?> type, String... possibleNames) {
		Field field = getField(owner, type, possibleNames);
		try {
			return MethodHandles.lookup().unreflectGetter(field);
		} catch (IllegalAccessException e) {
			//if it happens - eighter something has gone horribly wrong or the JVM is blocking access
			throw new Error(e);
		}
	}

	public static MethodHandle getFieldSetterHandle(Class<?> owner, Class<?> type, String... possibleNames) {
		Field field = getField(owner, type, possibleNames);
		try {
			return MethodHandles.lookup().unreflectSetter(field);
		} catch (IllegalAccessException e) {
			//if it happens - eighter something has gone horribly wrong or the JVM is blocking access
			throw new Error(e);
		}
	}

	/**
	 * Returns value of given field.
	 *
	 * Warning: Slow.
	 */
	public static <T> T getFieldFromSrg(Object from, String srgName) {
		String name = Mappings.getNameFromSrg(srgName);
		Class<?> cl = from.getClass();
		try {
			Field fld = cl.getDeclaredField(name);
			fld.setAccessible(true);
			return (T) fld.get(from);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
