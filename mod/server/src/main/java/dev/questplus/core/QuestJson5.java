package dev.questplus.core;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Strict, bounded JSON5 reader for quest definitions. Uses only Minecraft's
 * bundled Gson AST. Deliberately rejects non-finite numbers and executable code.
 */
public final class QuestJson5 {
	private QuestJson5() {}

	public static JsonElement parse(String source) {
		if (source == null || source.length() > 2_000_000)
			throw new IllegalArgumentException("Definition exceeds 2 MB");
		return new Parser(source).parse();
	}

	private static final class Parser {
		private final String source;
		private int pos;
		Parser(String source) { this.source = source; }

		JsonElement parse() {
			JsonElement result = value(0);
			space();
			if (pos != source.length()) throw error("Unexpected trailing content");
			return result;
		}

		private JsonElement value(int depth) {
			if (depth > 64) throw error("JSON5 nesting exceeds limit");
			space();
			if (pos >= source.length()) throw error("Missing value");
			char c = source.charAt(pos);
			if (c == '{') return object(depth + 1);
			if (c == '[') return array(depth + 1);
			if (c == '"' || c == '\'') return new JsonPrimitive(string());
			if (keyword("true")) return new JsonPrimitive(true);
			if (keyword("false")) return new JsonPrimitive(false);
			if (keyword("null")) return JsonNull.INSTANCE;
			if (starts("Infinity") || starts("NaN") || starts("-Infinity") || starts("+Infinity"))
				throw error("Non-finite JSON5 number");
			if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) return number();
			throw error("Unexpected value");
		}

		private JsonObject object(int depth) {
			pos++;
			JsonObject result = new JsonObject();
			space();
			if (eat('}')) return result;
			while (true) {
				space();
				String name = peek() == '"' || peek() == '\'' ? string() : identifier();
				space();
				if (!eat(':')) throw error("Expected ':'");
				result.add(name, value(depth));
				space();
				if (eat('}')) return result;
				if (!eat(',')) throw error("Expected ',' or '}'");
				space();
				if (eat('}')) return result;
			}
		}

		private JsonArray array(int depth) {
			pos++;
			JsonArray result = new JsonArray();
			space();
			if (eat(']')) return result;
			while (true) {
				result.add(value(depth));
				space();
				if (eat(']')) return result;
				if (!eat(',')) throw error("Expected ',' or ']'");
				space();
				if (eat(']')) return result;
			}
		}

		private String identifier() {
			if (pos >= source.length() || !startName(source.charAt(pos)))
				throw error("Invalid unquoted object key");
			int start = pos++;
			while (pos < source.length() && partName(source.charAt(pos))) pos++;
			return source.substring(start, pos);
		}
		private boolean startName(char c) { return c == '$' || c == '_' || Character.isUnicodeIdentifierStart(c); }
		private boolean partName(char c) { return c == '$' || c == '_' || Character.isUnicodeIdentifierPart(c); }

		private String string() {
			char quote=source.charAt(pos++);
			var out=new StringBuilder();
			while (pos < source.length()) {
				char c=source.charAt(pos++);
				if (c == quote) return out.toString();
				if (c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029')
					throw error("Unescaped line break in JSON5 string");
				if (c != '\\') {out.append(c); continue;}
				if (pos >= source.length()) throw error("Incomplete string escape");
				char e=source.charAt(pos++);
				switch(e) {
					case 'n' -> out.append('\n');
					case 'r' -> out.append('\r');
					case 't' -> out.append('\t');
					case 'b' -> out.append('\b');
					case 'f' -> out.append('\f');
					case 'v' -> out.append('\u000b');
					case '0' -> out.append('\0');
					case 'x' -> out.append((char)hexDigits(2));
					case 'u' -> out.append((char)hexDigits(4));
					case '\n' -> {}
					case '\r' -> {if (peek() == '\n') pos++;}
					case '\u2028', '\u2029' -> {}
					default -> out.append(e);
				}
			}
			throw error("Unterminated JSON5 string");
		}

		private int hexDigits(int count) {
			int result=0;
			for(int i=0;i<count;i++) {
				if(pos>=source.length()) throw error("Truncated hexadecimal escape");
				int digit=Character.digit(source.charAt(pos++),16);
				if(digit<0) throw error("Invalid hexadecimal escape");
				result=result*16+digit;
			}
			return result;
		}

		private JsonElement number() {
			int start=pos;
			boolean negative=eat('-');
			if (!negative) eat('+');
			if (peek()=='0' && (pos+1<source.length())
					&& (source.charAt(pos+1)=='x' || source.charAt(pos+1)=='X')) {
				pos+=2;
				int digits=pos;
				while(pos<source.length() && Character.digit(source.charAt(pos),16)>=0) pos++;
				if(pos==digits) throw error("Empty JSON5 hexadecimal number");
				BigInteger integer=new BigInteger(source.substring(digits,pos),16);
				return new JsonPrimitive(new BigDecimal(negative?integer.negate():integer));
			}
			int first=pos;
			while(pos<source.length() && Character.isDigit(source.charAt(pos))) pos++;
			boolean before=pos>first;
			boolean after=false;
			if(eat('.')) {
				int dot=pos;
				while(pos<source.length() && Character.isDigit(source.charAt(pos))) pos++;
				after=pos>dot;
			}
			if(!before&&!after) throw error("Invalid JSON5 number");
			if(eat('e')||eat('E')) {
				eat('+');
				if(peek()=='-') pos++;
				int exp=pos;
				while(pos<source.length() && Character.isDigit(source.charAt(pos))) pos++;
				if(exp==pos) throw error("Invalid JSON5 exponent");
			}
			try {
				return new JsonPrimitive(new BigDecimal(source.substring(start,pos)));
			} catch (NumberFormatException ex) {throw error("Invalid JSON5 number");}
		}

		private boolean keyword(String word) {
			if(!starts(word)) return false;
			int end=pos+word.length();
			if(end<source.length() && partName(source.charAt(end))) return false;
			pos=end; return true;
		}
		private boolean starts(String word) {return source.startsWith(word,pos);}
		private boolean eat(char c) {if(peek()!=c) return false;pos++;return true;}
		private char peek() {return pos<source.length()?source.charAt(pos):'\0';}

		private void space() {
			while(pos<source.length()) {
				char c=source.charAt(pos);
				if(Character.isWhitespace(c)||c=='\uFEFF'||c=='\u00a0') {pos++;continue;}
				if(c=='/' && pos+1<source.length()) {
					char next=source.charAt(pos+1);
					if(next=='/') {
						pos+=2;
						while(pos<source.length() && source.charAt(pos)!='\r' && source.charAt(pos)!='\n') pos++;
						continue;
					}
					if(next=='*') {
						int end=source.indexOf("*/",pos+2);
						if(end<0) throw error("Unclosed JSON5 block comment");
						pos=end+2;continue;
					}
				}
				break;
			}
		}
		private IllegalArgumentException error(String message) {
			return new IllegalArgumentException(message + " at character " + pos);
		}
	}
}
