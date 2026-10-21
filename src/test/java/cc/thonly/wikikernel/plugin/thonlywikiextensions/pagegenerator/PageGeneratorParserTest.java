package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 生成器解析 / 局部替换的回归测试：核心目标是“只改模板区域，不丢手写内容”。 */
class PageGeneratorParserTest {

    @Test
    void parsesStandardMainPageAndPreservesManualSections() {
        String source = """
                {{展会}}
                {{信息集合框
                |titlename=测试活动
                |图片=Test.png
                |活动名称=测试活动
                |活动别称=测试T
                |地区=广西壮族自治区南宁市
                }}
                {{活动信息
                |活动名称=测试活动
                |活动简称=测试T
                |主办方=测试组委会
                |地区=广西壮族自治区南宁市
                |类型=展会类活动
                |QQ群=123456
                }}
                {{历届信息
                |
                {{单届信息
                |[[测试活动/第一届|第一届]]
                |2026年10月1日
                |测试地点
                |10
                }}
                }}
                {{历届宣传画廊|
                <gallery mode="packed" heights="300px">
                A.png|宣传图
                </gallery>
                }}
                == 自定义章节 ==
                这是用户手写的内容。
                {{页面注释}}
                {{广西活动}}
                """;
        Optional<Map<String, Object>> parsed = PageGeneratorParser.parseMain("测试活动", source);
        assertTrue(parsed.isPresent());
        assertEquals("测试活动", parsed.get().get("displayName"));
        assertEquals("测试T", parsed.get().get("shortName"));
        assertEquals(1, ((List<?>) parsed.get().get("editions")).size());
        assertEquals(List.of("A.png|宣传图"), parsed.get().get("gallery"));

        Map<String, Object> payload = parsed.get();
        payload.put("shortName", "测试T2");
        String patched = PageGeneratorParser.patchMain("测试活动", source, payload);
        assertTrue(patched.contains("|活动简称=测试T2"));
        assertTrue(patched.contains("== 自定义章节 =="));
        assertTrue(patched.contains("这是用户手写的内容。"));
        assertTrue(patched.contains("A.png|宣传图"));
        assertFalse(patched.contains("{{需要补充}}"));
    }

    @Test
    void parsesStandardSubPageAndKeepsGalleryCaption() {
        String source = """
                {{具体展会}}
                {{具体展会/LOGO|SubLogo.png}}
                {{具体展会/简介}}
                {{具体展会/简介/日程
                |标题=Day 1
                |类型=即卖会
                |日期=2026年10月1日
                |地址=测试地址
                |摊位数=10
                }}
                == 返图 ==
                === Day 1 ===
                <gallery mode="packed" heights="300px">
                Photo.png|现场照片
                </gallery>
                == 自定义 ==
                手写内容
                {{页面注释}}
                """;
        Optional<Map<String, Object>> parsed = PageGeneratorParser.parseSub("测试活动/第一届", source);
        assertTrue(parsed.isPresent());
        assertEquals("测试活动", parsed.get().get("parent"));
        assertEquals("第一届", parsed.get().get("subName"));
        assertEquals(1, ((List<?>) parsed.get().get("days")).size());
        assertEquals(List.of("Photo.png|现场照片"), parsed.get().get("gallery"));

        Map<String, Object> payload = parsed.get();
        String patched = PageGeneratorParser.patchSub("测试活动/第一届", source, payload);
        assertTrue(patched.contains("SubLogo.png"));
        assertTrue(patched.contains("== 自定义 =="));
        assertTrue(patched.contains("手写内容"));
        assertTrue(patched.contains("Photo.png|现场照片"));
        assertTrue(patched.contains("{{页面注释}}"));
    }
}
