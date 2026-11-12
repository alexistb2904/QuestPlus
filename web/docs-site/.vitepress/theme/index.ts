import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import Feedback from "./Feedback.vue";
import Footer from "./Footer.vue";
import "./custom.css";
import "./home.css";

export default {
	extends: DefaultTheme,
	Layout: () =>
		h(DefaultTheme.Layout, null, {
			"doc-footer-before": () => h(Feedback),
			"layout-bottom": () => h(Footer),
		}),
};
