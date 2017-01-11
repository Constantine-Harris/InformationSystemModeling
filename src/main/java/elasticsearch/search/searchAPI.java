package elasticsearch.search;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Utils.ElasticSearchUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/search")
@Api(value = "search")
public class searchAPI {

	private Logger logger = LoggerFactory.getLogger(getClass());

	public static void main(String[] args) {
		new searchAPI().get("中国汽车", "新浪新闻", "", 1, 10, null);
	}

	
	@Path("/get")
	@GET
	@ApiOperation(value = "检索入口", notes = "输入检索关键字，展示结果列表")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "正常返回结果", response = searchResultBean.class)})
	@Consumes("application/x-www-form-urlencoded" + ";charset=" + "UTF-8")
	@Produces(MediaType.APPLICATION_JSON + ";charset=" + "UTF-8")
	public Response get(@DefaultValue("") @ApiParam(value = "key", required = true) @QueryParam("q") String q, // 搜索关键词
			@DefaultValue("新浪新闻") @ApiParam(value = "新闻来源", required = false) @QueryParam("newsSource") String newsSource,
			@DefaultValue("军事") @ApiParam(value = "新闻分类", required = false) @QueryParam("newsType") String newsType,
			@DefaultValue("1") @ApiParam(value = "分页功能，页数", required = false) @QueryParam("page") int page,
			@DefaultValue("10") @ApiParam(value = "分页功能，每页大小", required = false) @QueryParam("pagesize") int pagesize,
			@Context HttpServletRequest request) {
		/*
		 * 获取ip，并记录查询记录
		 */

		String ip = getip(request);
		logger.info("ip:" + ip);

		Client client = new ElasticSearchUtils().getClient();

		BoolQueryBuilder boolq = new BoolQueryBuilder();
		if (q.equals("")) {
			boolq.must(QueryBuilders.matchAllQuery());
			// multiMatchQuery(q, "newsTitle", "newsContent")
		} else {
			boolq.must(QueryBuilders.matchQuery("newsTitle", q));
			boolq.must(QueryBuilders.matchQuery("newsContent", q));
		}
		if (!newsType.equals("")) {
			boolq.must(QueryBuilders.matchPhraseQuery("newsType", newsType));
		}
		if (!newsSource.equals("")) {
			boolq.must(QueryBuilders.matchPhraseQuery("newsSource", newsSource));
		}

		
		/**
		 * 高亮设置
		 */
		HighlightBuilder hiBuilder = new HighlightBuilder();
		hiBuilder.preTags("<em>");
		hiBuilder.postTags("</em>");
		hiBuilder.field("newsTitle", 50);
		hiBuilder.field("newsContent", 150);

		
		/**
		 * 聚类.统计新闻来源和新闻的类型
		 */
		TermsAggregationBuilder AggnewsSource = AggregationBuilders.terms("newsSource").field("newsSource");
		TermsAggregationBuilder AggnewsType = AggregationBuilders.terms("newsType").field("newsType");

		
		/**
		 * 开始搜索
		 */

		SearchResponse response = client.prepareSearch("news").setTypes("article").setSearchType(SearchType.DEFAULT)
				.setQuery(boolq) // Query
				.setFrom(pagesize * (page - 1)).setSize(pagesize).addAggregation(AggnewsType)
				.addAggregation(AggnewsSource).highlighter(hiBuilder)
				// .addSort(sortbuilder)
				.setExplain(true) // 设置是否按查询匹配度排序
				.get();
		SearchHits myhits = response.getHits();
		logger.info("一共:" + myhits.getTotalHits() + " 结果");
		logger.info("耗时" + response.getTook());
		logger.info(response.toString());

		
		
		logger.info("开始统计聚合信息...");
		Aggregations aggs = response.getAggregations();
		Map<String, Long> newsSourceAggregation = new HashMap<>();
		for (Terms.Bucket entry : ((Terms) aggs.get("newsSource")).getBuckets()) {
			String key = (String) entry.getKey(); // Term
			long count = entry.getDocCount(); // Doc count
			newsSourceAggregation.put(key, count);
			logger.info(key + "-" + count);
		}

		
		Map<String, Long> newsTypeAggregation = new HashMap<>();
		for (Terms.Bucket entry : ((Terms) aggs.get("newsType")).getBuckets()) {
			String key = (String) entry.getKey(); // Term
			long count = entry.getDocCount(); // Doc count
			newsTypeAggregation.put(key, count);
			logger.info(key + "-" + count);
		}

		
		logger.info("开始收集高亮代码...");
		LinkedList<newsBean> newsList = new LinkedList<>();
		for (SearchHit hit : myhits) {
			Map<String, Object> map = hit.sourceAsMap();
			newsBean newsBean = new newsBean();

			// 获取title
			if (hit.getHighlightFields().containsKey("newsTitle")) {
				Text[] text = hit.getHighlightFields().get("newsTitle").getFragments();
				String content = "";
				for (Text str : text) {
					content = content + str;
				}
				newsBean.setNewsTitle(content);
			} else {
				logger.info(map.get("newsTitle").toString());
				newsBean.setNewsTitle(map.get("newsTitle").toString());
			}

			// 获取content
			if (hit.getHighlightFields().containsKey("newsContent")) {
				Text[] text = hit.getHighlightFields().get("newsContent").getFragments();
				String content = "";
				for (Text str : text) {
					content = content + str;
				}
				newsBean.setNewsContent(content);
			} else {
				logger.info(map.get("newsContent").toString());
				newsBean.setNewsTitle(map.get("newsContent").toString());
			}
			//获取其他
			newsBean.setNewsScratchTime(map.get("newsScratchTime").toString());
			newsBean.setNewsScratchTime(map.get("newsType").toString());
			newsBean.setNewsScratchTime(map.get("newsURL").toString());
			newsBean.setNewsScratchTime(map.get("newsSource").toString());
			
			newsList.add(newsBean);
		}
		
		/**
		 * 开始存储结果
		 */
		searchResultBean searchResult = new searchResultBean();
		searchResult.setPage(page);
		searchResult.setPagesize(pagesize);
		searchResult.setTotal(myhits.getTotalHits());
		searchResult.setUsetime(response.getTook().toString());
		searchResult.setNewsList(newsList);
		searchResult.setNewsSourceAggregation(newsSourceAggregation);
		searchResult.setNewsTypeAggregation(newsTypeAggregation);

		client.close();
		return Response.status(200).entity(searchResult).build();

	}

	public static String getip(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String ip = request.getHeader(" x-forwarded-for ");
		if (ip == null || ip.length() == 0 || " unknown ".equalsIgnoreCase(ip)) {
			ip = request.getHeader(" Proxy-Client-IP ");
		}
		if (ip == null || ip.length() == 0 || " unknown ".equalsIgnoreCase(ip)) {
			ip = request.getHeader(" WL-Proxy-Client-IP ");
		}
		if (ip == null || ip.length() == 0 || " unknown ".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

}