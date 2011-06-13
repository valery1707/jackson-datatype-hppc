package com.fasterxml.jackson.datatype.hppc.ser;

import java.io.IOException;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.type.JavaType;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.predicates.*;

public class ContainerSerializers
{
    public final static ContainerSerializerBase<?>[] _primitiveSerializers =
        new ContainerSerializerBase<?>[] {
                new ByteContainerSerializer(),
                new ShortContainerSerializer(),
                new IntContainerSerializer(),
                new LongContainerSerializer(),
                new CharContainerSerializer(),
                new FloatContainerSerializer(),
                new DoubleContainerSerializer()
        };

    /**
     * Method called to see if this serializer (or a serializer this serializer
     * knows) should be used for given type; if not, null is returned.
     */
    public static JsonSerializer<?> getMatchingSerializer(JavaType type)
    {
        for (ContainerSerializerBase<?> ser : _primitiveSerializers) {
            JsonSerializer<?> actual = ser.getSerializer(type);
            if (actual != null) {
                return actual;
            }
        }
        return null;
    }        
    
    /*
    /**********************************************************************
    /* Concrete container implementations; basic integral types
    /**********************************************************************
     */

    static class ByteContainerSerializer
        extends ContainerSerializerBase<ByteContainer>
    {
        ByteContainerSerializer() {
            super(ByteContainer.class, "integer");
        }
        
        @Override
        protected void serializeContents(final ByteContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            if (value instanceof ByteIndexedContainer) {
                ByteIndexedContainer list = (ByteIndexedContainer) value;
                for (int i = 0, len = list.size(); i < len; ++i) {
                    jgen.writeNumber(list.get(i));
                }
                return;
            }
            // doh. Can't throw checked exceptions through; hence need convoluted handling...
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new BytePredicate() {
                @Override
                public boolean apply(byte value) {
                    try {
                        jgen.writeNumber(value);
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }
    }

    final static class ShortContainerSerializer
        extends ContainerSerializerBase<ShortContainer>
    {
        ShortContainerSerializer() {
            super(ShortContainer.class, "integer");
        }
    
        @Override
        protected void serializeContents(final ShortContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            if (value instanceof ShortIndexedContainer) {
                ShortIndexedContainer list = (ShortIndexedContainer) value;
                for (int i = 0, len = list.size(); i < len; ++i) {
                    jgen.writeNumber(list.get(i));
                }
                return;
            }
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new ShortPredicate() {
                @Override
                public boolean apply(short value) {
                    try {
                        jgen.writeNumber(value);
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }
    }

    /**
     * Handler for HPPC containers that store int values.
     * Specific in that we actually implement separate optimal serializer
     * for indexed type, given how common this type is.
     */
    static class IntContainerSerializer
        extends ContainerSerializerBase<IntContainer>
    {
        IntContainerSerializer() {
            super(IntContainer.class, "integer");
        }

        // Overridden to allow use of more optimized serialized for indexed variant
        public JsonSerializer<?> getSerializer(JavaType type)
        {
            JsonSerializer<?> ser = super.getSerializer(type);
            if (ser != null) {
                if (IntIndexedContainer.class.isAssignableFrom(type.getClass())) {
                    return new Indexed();
                }
            }
            return ser;
        }
        
        @Override
        protected void serializeContents(final IntContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            // doh. Can't throw checked exceptions through; hence need convoluted handling...
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new IntPredicate() {
                @Override
                public boolean apply(int value) {
                    try {
                        jgen.writeNumber(value);
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }

        // Specialized variant to support indexed int container with more efficient accessor
        static class Indexed extends ContainerSerializerBase<IntIndexedContainer>
        {
            Indexed() {
                super(IntIndexedContainer.class, "integer");
            }

            @Override
            protected void serializeContents(final IntIndexedContainer value, final JsonGenerator jgen, SerializerProvider provider)
                   throws IOException, JsonGenerationException
            {
                for (int i = 0, len = value.size(); i < len; ++i) {
                    jgen.writeNumber(value.get(i));
                }
                return;
            }
        }
        
    }

    final static class LongContainerSerializer
        extends ContainerSerializerBase<LongContainer>
    {
        LongContainerSerializer() {
            super(LongContainer.class, "integer");
        }
    
        @Override
        protected void serializeContents(final LongContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            if (value instanceof LongIndexedContainer) {
                LongIndexedContainer list = (LongIndexedContainer) value;
                for (int i = 0, len = list.size(); i < len; ++i) {
                    jgen.writeNumber(list.get(i));
                }
                return;
            }
            // doh. Can't throw checked exceptions through; hence need convoluted handling...
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new LongPredicate() {
                @Override
                public boolean apply(long value) {
                    try {
                        jgen.writeNumber(value);
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }
    }

    /*
    /**********************************************************************
    /* Concrete container implementations; char
    /**********************************************************************
     */

    /**
     * This one is bit tricky: could serialize in multiple ways;
     * for example:
     *<ul>
     * <li>String that contains all characters (in order)</li>
     * <li>Array that contains single-character Strings</li>
     * <li>Array that contains numbers that represent character codes</li>
     *</ul>
     *
     * Let's start with second option; although first one may be the best
     * choice eventually.
     */
    final static class CharContainerSerializer
        extends ContainerSerializerBase<CharContainer>
    {
        CharContainerSerializer() {
            // no real 'char' type in JSON Schema; string seems better
            super(CharContainer.class, "string");
        }
    
        @Override
        protected void serializeContents(final CharContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            if (value instanceof CharIndexedContainer) {
                CharIndexedContainer list = (CharIndexedContainer) value;
                for (int i = 0, len = list.size(); i < len; ++i) {
                    jgen.writeString(String.valueOf(list.get(i)));
                }
                return;
            }
            // doh. Can't throw checked exceptions through; hence need convoluted handling...
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new CharPredicate() {
                @Override
                public boolean apply(char value) {
                    try {
                        jgen.writeString(String.valueOf(value));
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }
    }
    
    /*
    /**********************************************************************
    /* Concrete container implementations; floating-point types
    /**********************************************************************
     */

    final static class FloatContainerSerializer
        extends ContainerSerializerBase<FloatContainer>
    {
        FloatContainerSerializer() {
            super(FloatContainer.class, "number");
        }
    
        @Override
        protected void serializeContents(final FloatContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            if (value instanceof FloatIndexedContainer) {
                FloatIndexedContainer list = (FloatIndexedContainer) value;
                for (int i = 0, len = list.size(); i < len; ++i) {
                    jgen.writeNumber(list.get(i));
                }
                return;
            }
            // doh. Can't throw checked exceptions through; hence need convoluted handling...
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new FloatPredicate() {
                @Override
                public boolean apply(float value) {
                    try {
                        jgen.writeNumber(value);
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }
    }

    final static class DoubleContainerSerializer
        extends ContainerSerializerBase<DoubleContainer>
    {
        DoubleContainerSerializer() {
            super(DoubleContainer.class, "number");
        }
    
        @Override
        protected void serializeContents(final DoubleContainer value, final JsonGenerator jgen, SerializerProvider provider)
               throws IOException, JsonGenerationException
        {
            if (value instanceof DoubleIndexedContainer) {
                DoubleIndexedContainer list = (DoubleIndexedContainer) value;
                for (int i = 0, len = list.size(); i < len; ++i) {
                    jgen.writeNumber(list.get(i));
                }
                return;
            }
            // doh. Can't throw checked exceptions through; hence need convoluted handling...
            final ExceptionHolder holder = new ExceptionHolder();
            value.forEach(new DoublePredicate() {
                @Override
                public boolean apply(double value) {
                    try {
                        jgen.writeNumber(value);
                    } catch (IOException e) {
                        holder.assignException(e);
                        return false;
                    }
                    return true;
                }
            });
            holder.throwHeld();
        }
    }
}