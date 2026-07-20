(function () {
  const createElement = React.createElement;
  const BACKEND_API_BASE_URL = resolveBackendApiBaseUrl();
  const MAX_ANALYSIS_IMAGE_DIMENSION = 1200;
  const ANALYSIS_IMAGE_QUALITY = 0.86;
  const MAX_MATCH_CANDIDATES = 3;
  const MAX_SHOPPING_OPTIONS = 5;
  const FIELD_SUGGESTIONS = {
    category: ["top", "bottom"],
    color: ["black", "white", "navy", "blue", "gray", "beige", "brown", "green", "red", "pink", "purple", "yellow", "orange"],
    material: ["cotton", "linen", "denim", "knit", "ribbed knit", "chiffon", "satin", "leather", "wool", "polyester"],
    pattern: ["solid", "striped", "floral", "plaid", "checked", "polka dot", "graphic", "lace", "ribbed"],
    occasion: ["casual", "smart casual", "work", "formal", "party", "lounge", "athletic", "beach"],
    season: ["spring", "summer", "fall", "winter", "spring/summer", "fall/winter", "all season"]
  };

  const EMPTY_CLOTHING_ANALYSIS = {
    name: "",
    color: "",
    category: "",
    pattern: "",
    material: "",
    occasion: "",
    season: "",
  };

  const SAMPLE_WARDROBE_ITEMS = [
    { sampleKey: "sample-ivory-linen-shirt", name: "Ivory Linen Shirt", category: "top", colorA: "#f1eee6", colorB: "#d4c7b5", occasion: "smart casual" },
    { sampleKey: "sample-black-ribbed-tee", name: "Black Ribbed Tee", category: "top", colorA: "#14171a", colorB: "#575f66", occasion: "casual" },
    { sampleKey: "sample-sage-overshirt", name: "Sage Overshirt", category: "top", colorA: "#8fa58e", colorB: "#dce6d9", occasion: "street" },
    { sampleKey: "sample-indigo-straight-jeans", name: "Indigo Straight Jeans", category: "bottom", colorA: "#273f66", colorB: "#8aa0bd", occasion: "casual" },
    { sampleKey: "sample-charcoal-tailored-trouser", name: "Charcoal Tailored Trouser", category: "bottom", colorA: "#343a40", colorB: "#a2aab0", occasion: "formal" },
    { sampleKey: "sample-warm-sand-wide-pant", name: "Warm Sand Wide Pant", category: "bottom", colorA: "#c5a876", colorB: "#f0e4cd", occasion: "smart casual" }
  ];
  const SAMPLE_KEY_BY_NAME = new Map(
    SAMPLE_WARDROBE_ITEMS.map((sample) => [normalizeDuplicateText(sample.name), sample.sampleKey])
  );

  function WardrobeStudioApp() {
    const [wardrobeItems, setWardrobeItems] = React.useState([]);
    const [currentUser, setCurrentUser] = React.useState(null);
    const [isCheckingSession, setIsCheckingSession] = React.useState(true);
    const [activeWardrobeFilter, setActiveWardrobeFilter] = React.useState("all");
    const [selectedWardrobeItemId, setSelectedWardrobeItemId] = React.useState("");
    const [selectedImagePreview, setSelectedImagePreview] = React.useState("");
    const [selectedImageFingerprint, setSelectedImageFingerprint] = React.useState("");
    const [selectedFileName, setSelectedFileName] = React.useState("");
    const [recognizedClothingDetails, setRecognizedClothingDetails] = React.useState(EMPTY_CLOTHING_ANALYSIS);
    const [uploadState, setUploadState] = React.useState({ text: "", tone: "" });
    const [appState, setAppState] = React.useState({ text: "Ready for outfit analysis.", tone: "ready" });
    const [isRecognizingClothing, setIsRecognizingClothing] = React.useState(false);
    const [isScoringOutfits, setIsScoringOutfits] = React.useState(false);
    const [outfitScoreResults, setOutfitScoreResults] = React.useState([]);
    const [shoppingOptions, setShoppingOptions] = React.useState([]);
    const [shoppingStatus, setShoppingStatus] = React.useState({ text: "", tone: "" });
    const [isSearchingShopping, setIsSearchingShopping] = React.useState(false);
    const [shoppingTargetType, setShoppingTargetType] = React.useState("");
    const [isShoppingTypeMenuOpen, setIsShoppingTypeMenuOpen] = React.useState(false);
    const [activeSuggestionField, setActiveSuggestionField] = React.useState("");
    const [activeAppView, setActiveAppView] = React.useState("wardrobe");
    const [isDarkMode, setIsDarkMode] = React.useState(() => {
      const saved = localStorage.getItem("ai-wardrobe-theme");
      if (saved) return saved === "dark";
      return window.matchMedia("(prefers-color-scheme: dark)").matches;
    });
    const matcherPanelRef = React.useRef(null);
    const removedWardrobeItemIdsRef = React.useRef(new Set());
    const syncingWardrobeItemIdsRef = React.useRef(new Set());
    const deferredInstallPromptRef = React.useRef(null);
    const [isAppInstallable, setIsAppInstallable] = React.useState(false);
    const [isStandaloneApp, setIsStandaloneApp] = React.useState(false);
    const [isIosInstallHintVisible, setIsIosInstallHintVisible] = React.useState(false);

    React.useEffect(() => {
      fetch(`${BACKEND_API_BASE_URL}/api/auth/me`, { credentials: "include" })
        .then(async (response) => response.ok ? response.json() : null)
        .then((user) => setCurrentUser(user?.authenticated ? user : null))
        .catch(() => setCurrentUser(null))
        .finally(() => setIsCheckingSession(false));
    }, []);

    React.useEffect(() => {
      const standaloneQuery = window.matchMedia("(display-mode: standalone)");
      const isRunningStandalone = standaloneQuery.matches || window.navigator.standalone === true;
      setIsStandaloneApp(isRunningStandalone);

      function handleBeforeInstallPrompt(event) {
        event.preventDefault();
        deferredInstallPromptRef.current = event;
        setIsAppInstallable(true);
      }

      function handleAppInstalled() {
        deferredInstallPromptRef.current = null;
        setIsAppInstallable(false);
        setIsStandaloneApp(true);
      }

      window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
      window.addEventListener("appinstalled", handleAppInstalled);

      const isIos = /iphone|ipad|ipod/i.test(window.navigator.userAgent);
      if (isIos && !isRunningStandalone && !localStorage.getItem("ai-wardrobe-ios-install-hint-dismissed")) {
        setIsIosInstallHintVisible(true);
      }

      return () => {
        window.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
        window.removeEventListener("appinstalled", handleAppInstalled);
      };
    }, []);

    async function handleInstallAppClick() {
      const deferredPrompt = deferredInstallPromptRef.current;
      if (!deferredPrompt) return;
      deferredPrompt.prompt();
      await deferredPrompt.userChoice.catch(() => {});
      deferredInstallPromptRef.current = null;
      setIsAppInstallable(false);
    }

    function dismissIosInstallHint() {
      localStorage.setItem("ai-wardrobe-ios-install-hint-dismissed", "1");
      setIsIosInstallHintVisible(false);
    }

    React.useEffect(() => {
      document.documentElement.setAttribute("data-theme", isDarkMode ? "dark" : "light");
      localStorage.setItem("ai-wardrobe-theme", isDarkMode ? "dark" : "light");
    }, [isDarkMode]);

    React.useEffect(() => {
      let isCancelled = false;
      if (!currentUser) return undefined;
      fetchStoredWardrobeItems()
        .then((storedItems) => {
          if (isCancelled) return;
          setWardrobeItems((currentItems) => {
            const sampleItems = currentItems.filter(isSampleWardrobeItem);
            const localItems = currentItems.filter((item) => !isSampleWardrobeItem(item));
            const r2Items = storedItems.map(normalizeWardrobeItem);
            const refreshedItems = deduplicateWardrobeItems(r2Items.concat(localItems, sampleItems));
            setAppState({ text: "Wardrobe synced from R2.", tone: "ready" });
            return refreshedItems;
          });
        })
        .catch(() => {
          if (!isCancelled) {
            setAppState({ text: "Could not load wardrobe items from R2. Local items are still available.", tone: "error" });
          }
        });
      return () => {
        isCancelled = true;
      };
    }, [currentUser?.username]);

    React.useEffect(() => {
      wardrobeItems
        .filter((item) => !isSampleWardrobeItem(item))
        .filter((item) => !item.cloudStorage?.stored)
        .filter((item) => !syncingWardrobeItemIdsRef.current.has(item.id))
        .forEach((item) => {
          syncingWardrobeItemIdsRef.current.add(item.id);
          backupWardrobeItemToR2(item).finally(() => {
            syncingWardrobeItemIdsRef.current.delete(item.id);
          });
        });
    }, [wardrobeItems]);

    const selectedWardrobeItem = wardrobeItems.find((item) => item.id === selectedWardrobeItemId);
    const outfitCandidateItems = selectedWardrobeItem ? wardrobeItems.filter((item) => item.id !== selectedWardrobeItem.id && canItemsCreateOutfitPair(selectedWardrobeItem, item)) : [];
    const displayedWardrobeItems = activeWardrobeFilter === "all" ? wardrobeItems : wardrobeItems.filter((item) => item.category === activeWardrobeFilter);
    const topItemCount = wardrobeItems.filter((item) => item.category === "top").length;
    const bottomItemCount = wardrobeItems.filter((item) => item.category === "bottom").length;
    React.useEffect(() => {
      setOutfitScoreResults([]);
      setShoppingOptions([]);
      setShoppingStatus({ text: "", tone: "" });
      setShoppingTargetType("");
      setIsShoppingTypeMenuOpen(false);
      setIsScoringOutfits(false);
    }, [selectedWardrobeItemId]);

    async function handleClothingImageSelected(event) {
      const selectedFile = event.target.files && event.target.files[0];
      if (!selectedFile) return;
      setIsRecognizingClothing(true);
      setSelectedFileName(selectedFile.name);
      setUploadState({ text: "AI recognizing...", tone: "busy" });

      try {
        const uploadedImageDataUrl = await readImageFileAsOptimizedDataUrl(selectedFile);
        const imageFingerprint = await createImageFingerprint(uploadedImageDataUrl);
        const duplicateItem = findDuplicateWardrobeItem(wardrobeItems, {
          imageFingerprint,
          image: uploadedImageDataUrl
        });

        if (duplicateItem) {
          setSelectedImagePreview("");
          setSelectedImageFingerprint("");
          setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
          setUploadState({ text: `Already saved as ${duplicateItem.analysis?.name || "an item"}.`, tone: "error" });
          return;
        }

        setSelectedImagePreview(uploadedImageDataUrl);
        setSelectedImageFingerprint(imageFingerprint);
        const analysisResult = await sendJsonToBackend("/api/analyze-clothing", { image: uploadedImageDataUrl });
        setRecognizedClothingDetails(normalizeClothingAnalysis(analysisResult));
        setUploadState({ text: "AI recognized.", tone: "ready" });
      } catch (error) {
        setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
        setUploadState({ text: error.message || "Could not analyze this image.", tone: "error" });
      } finally {
        setIsRecognizingClothing(false);
        event.target.value = "";
      }
    }

    function cancelSelectedUpload() {
      setSelectedImagePreview("");
      setSelectedImageFingerprint("");
      setSelectedFileName("");
      setRecognizedClothingDetails(EMPTY_CLOTHING_ANALYSIS);
      setUploadState({ text: "", tone: "" });
    }

    function updateRecognizedClothingField(fieldName, value) {
      setRecognizedClothingDetails((currentDetails) => Object.assign({}, currentDetails, {
        [fieldName]: value
      }));
    }

    function saveRecognizedClothingItem() {
      if (!selectedImagePreview || !recognizedClothingDetails.name || !recognizedClothingDetails.category) return;
      const editedClothingDetails = normalizeClothingAnalysis(recognizedClothingDetails);
      const duplicateItem = findDuplicateWardrobeItem(wardrobeItems, {
        imageFingerprint: selectedImageFingerprint,
        image: selectedImagePreview,
        analysis: editedClothingDetails
      });

      if (duplicateItem) {
        cancelSelectedUpload();
        setUploadStatusForDuplicate(duplicateItem);
        return;
      }

      const wardrobeItem = {
        id: createWardrobeItemId(),
        image: selectedImagePreview,
        imageFingerprint: selectedImageFingerprint,
        originalFileName: selectedFileName,
        analysis: editedClothingDetails,
        category: editedClothingDetails.category,
        createdAt: new Date().toISOString()
      };
      setWardrobeItems((currentItems) => deduplicateWardrobeItems([wardrobeItem].concat(currentItems)));
      cancelSelectedUpload();
      setAppState({ text: "Item saved locally. Syncing to R2...", tone: "busy" });
      backupWardrobeItemToR2(wardrobeItem);
    }

    function removeWardrobeItem(itemId, event) {
      event.stopPropagation();
      const item = wardrobeItems.find((i) => i.id === itemId);
      const removeItemLocally = () => {
        setWardrobeItems((currentItems) => currentItems.filter((item) => item.id !== itemId));
        if (selectedWardrobeItemId === itemId) setSelectedWardrobeItemId("");
        setOutfitScoreResults([]);
      };

      if (!item || isSampleWardrobeItem(item)) {
        removeItemLocally();
        return;
      }

      if (!isSampleWardrobeItem(item)) {
        removedWardrobeItemIdsRef.current.add(itemId);
        deleteStoredWardrobeItem(itemId)
          .then(removeItemLocally)
          .catch((error) => {
            removedWardrobeItemIdsRef.current.delete(itemId);
            setAppState({ text: error.message || "Could not delete the R2 copy, so the item was kept.", tone: "error" });
          });
      }
    }

    function selectWardrobeItem(itemId) {
      setSelectedWardrobeItemId(itemId);
      setOutfitScoreResults([]);
      setIsScoringOutfits(false);
      setActiveAppView("match");
      requestAnimationFrame(() => {
        matcherPanelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    }

    function loadSampleWardrobeItems() {
      setWardrobeItems((currentItems) => {
        const userWardrobeItems = deduplicateWardrobeItems(currentItems).filter((item) => !isSampleWardrobeItem(item));
        const sampleItems = SAMPLE_WARDROBE_ITEMS.map(createSampleWardrobeItem);
        const refreshedWardrobeItems = deduplicateWardrobeItems(sampleItems.concat(userWardrobeItems));
        return refreshedWardrobeItems;
      });
    }

    function createSampleWardrobeItem({ sampleKey, name, category, colorA, colorB, occasion }) {
      const image = createSampleWardrobeItemImage(name, category, colorA, colorB);
      return {
        id: sampleKey,
        sampleKey,
        imageFingerprint: sampleKey,
        image,
        originalFileName: "sample",
        category,
        createdAt: new Date().toISOString(),
        analysis: {
          name,
          color: name.split(" ")[0].toLowerCase(),
          category,
          pattern: name.includes("Jeans") ? "denim" : "solid",
          material: name.includes("Linen") ? "linen" : name.includes("Ribbed") ? "cotton knit" : "woven fabric",
          occasion,
          season: "all season"
        }
      };
    }

    function clearWardrobe() {
      if (!wardrobeItems.some(isSampleWardrobeItem)) {
        return;
      }

      if (!confirm("Clear sample wardrobe items? Uploaded items will stay saved.")) return;
      setWardrobeItems((currentItems) => {
        const selectedItemWillBeRemoved = currentItems.some((item) => item.id === selectedWardrobeItemId && isSampleWardrobeItem(item));
        const uploadedItems = currentItems.filter((item) => !isSampleWardrobeItem(item));
        if (selectedItemWillBeRemoved) setSelectedWardrobeItemId("");
        setOutfitScoreResults([]);
        return uploadedItems;
      });
    }

    async function analyzeSelectedItemOutfitScores() {
      if (!selectedWardrobeItem || !outfitCandidateItems.length) return;
      setIsScoringOutfits(true);
      setOutfitScoreResults([]);

      try {
        const batchResult = await sendJsonToBackend("/api/score-outfits", {
          selectedLabel: buildWardrobeItemDescription(selectedWardrobeItem),
          candidates: outfitCandidateItems.map((candidate) => ({
            id: candidate.id,
            label: buildWardrobeItemDescription(candidate)
          }))
        });
        const candidateById = new Map(outfitCandidateItems.map((candidate) => [candidate.id, candidate]));
        const scored = (batchResult.results || []).map((result) => ({
          candidateId: result.candidateId,
          candidate: candidateById.get(result.candidateId),
          score: clampOutfitScore(result.score),
          verdict: result.verdict || ""
        })).filter((result) => result.candidate);

        const top3 = scored
          .sort((a, b) => b.score - a.score)
          .slice(0, MAX_MATCH_CANDIDATES);

        setOutfitScoreResults(top3);
      } catch (error) {
        setAppState({ text: error.message || "AI matching failed.", tone: "error" });
      } finally {
        setIsScoringOutfits(false);
      }
    }

    async function findShoppingOptions() {
      if (!selectedWardrobeItem) return;
      if (!shoppingTargetType) {
        setShoppingStatus({ text: "Choose what type of item you want to shop for.", tone: "error" });
        return;
      }
      setIsSearchingShopping(true);
      setShoppingOptions([]);
      setShoppingStatus({ text: "Searching stores...", tone: "busy" });
      try {
        const options = await sendJsonToBackend("/api/shopping-options", {
          selectedItem: buildWardrobeItemDescription(selectedWardrobeItem),
          targetCategory: selectedWardrobeItem.category === "bottom" ? "top" : "bottom",
          targetType: shoppingTargetType
        });
        const visibleOptions = Array.isArray(options) ? options.slice(0, MAX_SHOPPING_OPTIONS) : [];
        setShoppingOptions(visibleOptions);
        setShoppingStatus(visibleOptions.length
          ? { text: `Showing ${visibleOptions.length} online matches.`, tone: "ready" }
          : { text: "No online products were found. Try another type.", tone: "error" });
      } catch (error) {
        setShoppingStatus({ text: error.message || "Online shopping search failed.", tone: "error" });
      } finally {
        setIsSearchingShopping(false);
      }
    }

    async function backupWardrobeItemToR2(wardrobeItem) {
      try {
        const storageResult = await sendJsonToBackend("/api/wardrobe-items", wardrobeItem);
        if (removedWardrobeItemIdsRef.current.has(wardrobeItem.id)) {
          await deleteStoredWardrobeItem(wardrobeItem.id);
          removedWardrobeItemIdsRef.current.delete(wardrobeItem.id);
          return;
        }

        setWardrobeItems((currentItems) => currentItems.map((item) =>
          item.id === wardrobeItem.id ? Object.assign({}, item, { cloudStorage: storageResult }) : item
        ));
        setAppState({ text: "Item synced to R2.", tone: "ready" });
      } catch (error) {
        setAppState({ text: `Item saved locally. R2 sync skipped: ${error.message || "backend is not reachable."}`, tone: "error" });
      }
    }

    async function deleteStoredWardrobeItem(itemId) {
      const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/wardrobe-items/${encodeURIComponent(itemId)}`, { method: "DELETE" });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.error || result.detail || "Item removed locally, but the R2 copy could not be deleted.");
      }
      return result;
    }

    async function logout() {
      await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/logout`, { method: "POST" }).catch(() => {});
      setWardrobeItems([]);
      setCurrentUser(null);
    }

    const RANK_LABELS = ["#1 Best Match", "#2 Runner Up", "#3 Also Good"];

    if (isCheckingSession) return createElement("div", { className: "auth-shell" }, createElement("p", null, "Loading your wardrobe..."));
    if (!currentUser) return createElement(AuthScreen, { onAuthenticated: setCurrentUser });

    return createElement("div", { className: "app" },
      createElement("header", { className: "topbar" },
        createElement("div", { className: "brand" },
          createElement("div", { className: "brand-mark" }, "AW"),
          createElement("div", null,
            createElement("h1", null, "AI Wardrobe")
          )
        ),
        createElement("div", { className: "topbar-actions" },
          createElement("span", { className: "account-name", title: currentUser.username }, currentUser.username),
          isAppInstallable && !isStandaloneApp
            ? createElement("button", {
                className: "install-app-button",
                onClick: handleInstallAppClick,
                type: "button",
                title: "Install AI Wardrobe as an app"
              }, "Install App")
            : null,
          createElement("button", {
            className: "theme-toggle",
            onClick: () => setIsDarkMode(d => !d),
            title: isDarkMode ? "Switch to light mode" : "Switch to dark mode",
            type: "button"
          }, isDarkMode ? "Light" : "Dark"),
          createElement("button", { className: "logout-button", onClick: logout, type: "button" }, "Log out")
        )
      ),
      isIosInstallHintVisible
        ? createElement("div", { className: "ios-install-hint", role: "status" },
            createElement("span", null, "Install this app: tap Share, then \"Add to Home Screen\"."),
            createElement("button", {
              className: "icon-button",
              onClick: dismissIosInstallHint,
              type: "button",
              title: "Dismiss"
            }, "x")
          )
        : null,
      createElement("main", { className: "shell", "data-view": activeAppView },
        createElement("aside", { className: "left-rail" },
          createElement("section", { className: "panel upload-box app-screen add-screen" },
            createElement("div", null,
              createElement("h2", null, "Add item")
            ),
            selectedImagePreview
              ? createElement("div", { className: "preview" },
                  createElement("button", { className: "icon-button cancel-upload", onClick: cancelSelectedUpload, type: "button", title: "Cancel upload" }, "x"),
                  createElement("img", { src: selectedImagePreview, alt: "Selected clothing preview" })
                )
              : createElement("label", { className: "dropzone" },
                  createElement("input", { type: "file", accept: "image/*", onChange: handleClothingImageSelected }),
                  createElement("strong", null, "Choose image"),
                  createElement("span", null, "PNG, JPG, or WEBP clothing photo")
                ),
            uploadState.text ? createElement("p", { className: "status " + uploadState.tone }, uploadState.text) : null,
            createElement("div", { className: "recognition" },
              createElement("div", { className: "data-grid" },
                renderEditableAnalysisField("Name", "name", recognizedClothingDetails.name, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Category", "category", recognizedClothingDetails.category, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Color", "color", recognizedClothingDetails.color, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Material", "material", recognizedClothingDetails.material, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Pattern", "pattern", recognizedClothingDetails.pattern, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Occasion", "occasion", recognizedClothingDetails.occasion, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField),
                renderEditableAnalysisField("Season", "season", recognizedClothingDetails.season, updateRecognizedClothingField, activeSuggestionField, setActiveSuggestionField)
              )
            ),
            createElement("div", { className: "actions" },
              createElement("button", { className: "primary", onClick: saveRecognizedClothingItem, disabled: isRecognizingClothing || !selectedImagePreview || !recognizedClothingDetails.name, type: "button" }, "Save item"),
              createElement("button", { className: "secondary", onClick: cancelSelectedUpload, disabled: !selectedImagePreview && !recognizedClothingDetails.name, type: "button" }, "Cancel")
            )
          ),
        ),
        createElement("section", { className: "main" },
          createElement("section", { className: "panel app-screen wardrobe-screen" },
            createElement("div", { className: "wardrobe-head" },
              createElement("h2", null, "Wardrobe"),
              createElement("div", { className: "wardrobe-controls" },
                createElement("div", { className: "filters" },
                  renderWardrobeFilterButton("all", activeWardrobeFilter, setActiveWardrobeFilter, wardrobeItems.length),
                  renderWardrobeFilterButton("top", activeWardrobeFilter, setActiveWardrobeFilter, topItemCount),
                  renderWardrobeFilterButton("bottom", activeWardrobeFilter, setActiveWardrobeFilter, bottomItemCount)
                ),
                createElement("div", { className: "sample-actions" },
                  createElement("button", { className: "secondary", onClick: loadSampleWardrobeItems, type: "button" }, "Samples"),
                  createElement("button", { className: "danger", onClick: clearWardrobe, type: "button" }, "Clear")
                )
                ,
                createElement("div", { className: "shopping-fallback" },
                  createElement("p", null, "Not feeling these matches? Shop complementary options online."),
                  renderShoppingTypeSelector(selectedWardrobeItem, shoppingTargetType, setShoppingTargetType, isShoppingTypeMenuOpen, setIsShoppingTypeMenuOpen),
                  createElement("button", {
                    className: "secondary",
                    type: "button",
                    onClick: findShoppingOptions,
                    disabled: isSearchingShopping || !shoppingTargetType
                  }, isSearchingShopping ? "Searching stores..." : "Shop online"),
                  shoppingStatus.text ? createElement("p", { className: "status " + shoppingStatus.tone }, shoppingStatus.text) : null,
                  shoppingOptions.length
                    ? createElement("div", { className: "shopping-results" }, shoppingOptions.map((option, index) =>
                        createElement("a", {
                          className: "shopping-card",
                          href: option.url,
                          target: "_blank",
                          rel: "noopener noreferrer",
                          key: option.url || index
                        },
                          option.image ? createElement("img", { src: option.image, alt: "" }) : null,
                          createElement("span", null,
                            createElement("strong", null, option.title),
                            createElement("small", null, [option.price, option.store].filter(Boolean).join(" · "))
                          ),
                          createElement("b", null, "Buy")
                        )
                      ))
                    : null
                )
              )
            ),
            displayedWardrobeItems.length
              ? createElement("div", { className: "wardrobe-grid" }, displayedWardrobeItems.map((item) =>
                  createElement("div", {
                    key: item.id,
                    role: "button",
                    tabIndex: 0,
                    className: "item-card " + (item.id === selectedWardrobeItemId ? "selected" : ""),
                    onClick: () => {
                      selectWardrobeItem(item.id);
                    },
                    onKeyDown: (event) => {
                      if (event.key !== "Enter" && event.key !== " ") return;
                      event.preventDefault();
                      selectWardrobeItem(item.id);
                    }
                  },
                    createElement("button", {
                      className: "icon-button remove-item",
                      type: "button",
                      title: "Cancel item",
                      onClick: (event) => removeWardrobeItem(item.id, event)
                    }, "x"),
                    createElement("img", { src: item.image, alt: item.analysis.name }),
                    createElement("div", { className: "item-info" },
                      createElement("strong", null, item.analysis.name),
                      createElement("div", { className: "chips" },
                        createElement("span", { className: "chip" }, item.category),
                        createElement("span", { className: "chip" }, item.analysis.color || item.analysis.primaryColor || "color"),
                        createElement("span", { className: "chip" }, item.analysis.material || "material"),
                        createElement("span", { className: "chip" }, item.analysis.pattern || "pattern"),
                        createElement("span", { className: "chip" }, item.analysis.occasion || "occasion"),
                        createElement("span", { className: "chip" }, item.analysis.season || "season")
                      )
                    )
                  )
                ))
              : createElement("div", { className: "empty" }, "No wardrobe items yet. Upload a photo or load samples.")
          ),
          createElement("section", { className: "score-panel app-screen match-screen", ref: matcherPanelRef },
            createElement("div", { className: "score-head" },
              createElement("div", null,
                createElement("h2", null, "Matching")
              ),
              createElement("button", { className: "primary", onClick: () => analyzeSelectedItemOutfitScores(), disabled: !selectedWardrobeItem || !outfitCandidateItems.length || isScoringOutfits, type: "button" },
                isScoringOutfits ? "Matching..." : "Match"
              )
            ),
            selectedWardrobeItem
              ? createElement("div", { className: "selected-item-preview" },
                  createElement("div", { className: "selected-item-image-wrap" },
                    createElement("img", { src: selectedWardrobeItem.image, alt: selectedWardrobeItem.analysis.name })
                  ),
                  createElement("span", { className: "selected-item-badge" }, "Selected")
                )
              : null,
            outfitScoreResults.length
              ? createElement("div", { className: "top3-results" },
                  outfitScoreResults.map((result, index) =>
                    createElement("div", { key: result.candidateId, className: "match-card" },
                      createElement("div", { className: "match-rank" }, RANK_LABELS[index] || `#${index + 1}`),
                      createElement("img", { className: "match-candidate-image", src: result.candidate.image, alt: result.candidate.analysis.name }),
                      createElement("div", { className: "match-info" },
                        createElement("strong", null, result.candidate.analysis.name),
                        result.verdict && createElement("p", { className: "match-verdict" }, result.verdict)
                      ),
                      createElement("div", { className: "score-number", style: { "--score": result.score } }, `${result.score}%`)
                    )
                  ),
                  createElement("div", { className: "shopping-fallback match-shopping-fallback" },
                    createElement("p", null, "Not feeling these matches? Shop complementary options online."),
                    renderShoppingTypeSelector(selectedWardrobeItem, shoppingTargetType, setShoppingTargetType, isShoppingTypeMenuOpen, setIsShoppingTypeMenuOpen),
                    createElement("button", {
                    className: "secondary",
                    type: "button",
                    onClick: findShoppingOptions,
                    disabled: isSearchingShopping || !shoppingTargetType
                  }, isSearchingShopping ? "Searching stores..." : "Shop online"),
                    shoppingStatus.text ? createElement("p", { className: "status " + shoppingStatus.tone }, shoppingStatus.text) : null,
                    shoppingOptions.length
                      ? createElement("div", { className: "shopping-results" }, shoppingOptions.map((option, index) =>
                          createElement("a", {
                            className: "shopping-card",
                            href: option.url,
                            target: "_blank",
                            rel: "noopener noreferrer",
                            key: option.url || index
                          },
                            option.image ? createElement("img", { src: option.image, alt: "" }) : null,
                            createElement("span", null,
                              createElement("strong", null, option.title),
                              createElement("small", null, [option.price, option.store].filter(Boolean).join(" · "))
                            ),
                            createElement("b", null, "Buy")
                          )
                        ))
                      : null
                  )
                )
              : isScoringOutfits
                ? createElement("div", { className: "empty" }, "Scoring all matches...")
                : selectedWardrobeItem
                  ? null
                  : createElement("div", { className: "empty" }, "Select an item from your wardrobe to start matching.")
          )
        )
      ),
      createElement("nav", { className: "app-tabs", "aria-label": "Primary" },
        renderAppTabButton("add", "Add", activeAppView, setActiveAppView),
        renderAppTabButton("wardrobe", "Wardrobe", activeAppView, setActiveAppView),
        renderAppTabButton("match", "Match", activeAppView, setActiveAppView)
      )
    );
  }

  function resolveBackendApiBaseUrl() {
    const pageHostname = window.location.hostname;
    if (!pageHostname || pageHostname === "localhost" || pageHostname === "127.0.0.1") {
      return "http://127.0.0.1:8080";
    }

    const configuredUrl = trimText(
      window.AI_WARDROBE_API_URL ||
      localStorage.getItem("ai-wardrobe-api-url")
    );
    if (configuredUrl) return configuredUrl.replace(/\/+$/, "");

    return `http://${pageHostname}:8080`;
  }

  function renderAppTabButton(viewName, label, activeView, setActiveView) {
    return createElement("button", {
      type: "button",
      className: activeView === viewName ? "active" : "",
      onClick: () => setActiveView(viewName),
      "aria-current": activeView === viewName ? "page" : undefined
    }, label);
  }

  function renderEditableAnalysisField(label, fieldName, value, onChange, activeField, setActiveField) {
    const suggestions = FIELD_SUGGESTIONS[fieldName] || [];
    const normalizedValue = trimText(value).toLowerCase();
    const matchingSuggestions = normalizedValue
      ? suggestions.filter((suggestion) => suggestion.includes(normalizedValue) && suggestion !== normalizedValue).slice(0, 5)
      : [];
    return createElement("label", { className: "field" },
      createElement("span", null, label),
      createElement("input", {
        value: value || "",
        autoComplete: "off",
        onFocus: () => setActiveField(fieldName),
        onBlur: () => setActiveField(""),
        onChange: (event) => {
          setActiveField(fieldName);
          onChange(fieldName, event.target.value);
        }
      }),
      activeField === fieldName && matchingSuggestions.length
        ? createElement("div", { className: "field-suggestions" },
            matchingSuggestions.map((suggestion) =>
              createElement("button", {
                type: "button",
                key: suggestion,
                onMouseDown: (event) => {
                  event.preventDefault();
                  onChange(fieldName, suggestion);
                  setActiveField("");
                }
              }, suggestion)
            )
          )
        : null
    );
  }

  function renderWardrobeFilterButton(filterValue, activeFilter, setActiveFilter, count) {
    const filterLabel = filterValue === "all" ? "All" : filterValue === "top" ? "Tops" : "Bottoms";
    return createElement("button", {
      type: "button",
      className: activeFilter === filterValue ? "active" : "",
      onClick: () => setActiveFilter(filterValue)
    }, `${filterLabel} (${count})`);
  }

  function renderShoppingTypeSelector(selectedItem, value, onChange, isOpen, setIsOpen) {
    if (!selectedItem) return null;
    const options = selectedItem.category === "bottom"
      ? [
          ["", "Choose a top type"],
          ["blouse", "Blouse"],
          ["shirt", "Shirt"],
          ["t-shirt", "T-shirt"],
          ["any-top", "Any top"]
        ]
      : [
          ["", "Choose a bottom type"],
          ["pants", "Pants"],
          ["skirt", "Skirt"],
          ["shorts", "Shorts"],
          ["any-bottom", "Any bottom"]
        ];
    const selectedOption = options.find(([optionValue]) => optionValue === value);
    return createElement("div", { className: "shopping-type-field" },
      createElement("span", null, "What would you like to wear?"),
      createElement("button", {
        className: "shopping-type-trigger",
        type: "button",
        "aria-expanded": isOpen,
        onClick: () => setIsOpen(!isOpen)
      },
        createElement("span", null, selectedOption ? selectedOption[1] : options[0][1]),
        createElement("b", null, isOpen ? "▲" : "▼")
      ),
      isOpen
        ? createElement("div", { className: "shopping-type-menu" },
            options.slice(1).map(([optionValue, label]) =>
              createElement("button", {
                className: optionValue === value ? "active" : "",
                type: "button",
                key: optionValue,
                onClick: () => {
                  onChange(optionValue);
                  setIsOpen(false);
                }
              }, label)
            )
          )
        : null
    );
  }

  function normalizeClothingAnalysis(result) {
    result = result || {};
    const normalizedName = trimText(result.name) || "Recognized clothing item";
    const normalizedCategory = /\b(dress|jumpsuit|romper)\b/i.test(normalizedName)
      ? "dress"
      : normalizeCategory(result.category);
    return Object.assign({}, EMPTY_CLOTHING_ANALYSIS, {
      name: normalizedName,
      color: trimText(result.color || result.primaryColor),
      category: normalizedCategory,
      pattern: trimText(result.pattern),
      material: trimText(result.material),
      occasion: trimText(result.occasion),
      season: trimText(result.season)
    });
  }

  function normalizeCategory(value) {
    const category = trimText(value).toLowerCase();
    if (["dress", "dresses", "jumpsuit", "romper", "one-piece", "one piece"].includes(category)) {
      return "dress";
    }
    if (["bottom", "bottoms", "pant", "pants", "jean", "jeans", "trouser", "trousers", "skirt", "short", "shorts"].includes(category)) {
      return "bottom";
    }
    if (["top", "tops", "shirt", "tshirt", "t-shirt", "tee", "blouse", "hoodie", "sweatshirt", "tank", "tank top", "jacket", "cardigan"].includes(category)) {
      return "top";
    }
    return category || "top";
  }

  function canItemsCreateOutfitPair(firstItem, secondItem) {
    return (firstItem.category === "top" && secondItem.category === "bottom") || (firstItem.category === "bottom" && secondItem.category === "top");
  }

  function buildWardrobeItemDescription(item) {
    const details = item.analysis || {};
    return [
      details.name,
      `category: ${details.category}`,
      `color: ${details.color || details.primaryColor}`,
      `pattern: ${details.pattern}`,
      `material: ${details.material}`,
      `occasion: ${details.occasion}`,
      `season: ${details.season}`
    ].filter(Boolean).join("; ");
  }

  function normalizeWardrobeItem(item) {
    item = item || {};
    const analysis = normalizeClothingAnalysis(item.analysis);
    return Object.assign({}, item, {
      analysis,
      category: analysis.category === "dress"
        ? "dress"
        : trimText(item.category || analysis.category) || "top"
    });
  }

  function clampOutfitScore(value) {
    const score = Math.round(Number(value));
    if (Number.isNaN(score)) return 0;
    return Math.max(0, Math.min(100, score));
  }

  function trimText(value) {
    return String(value || "").trim();
  }

  function createWardrobeItemId() {
    return `item_${Date.now()}_${Math.random().toString(16).slice(2)}`;
  }

  function createSampleKeyFromName(name) {
    const knownSampleKey = SAMPLE_KEY_BY_NAME.get(normalizeDuplicateText(name));
    if (knownSampleKey) return knownSampleKey;
    return `sample-${normalizeDuplicateText(name).replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")}`;
  }

  function normalizeDuplicateText(value) {
    return String(value || "").trim().toLowerCase().replace(/\s+/g, " ");
  }

  function deduplicateWardrobeItems(items) {
    const seenKeys = new Set();
    return items.filter((item) => {
      const duplicateKeys = getWardrobeDuplicateKeys(item);
      if (!duplicateKeys.length) return true;
      if (duplicateKeys.some((key) => seenKeys.has(key))) return false;
      duplicateKeys.forEach((key) => seenKeys.add(key));
      return true;
    });
  }

  function findDuplicateWardrobeItem(items, candidateItem) {
    const candidateKeys = getWardrobeDuplicateKeys(candidateItem);
    if (!candidateKeys.length) return null;

    return items.find((item) => {
      const existingKeys = getWardrobeDuplicateKeys(item);
      return candidateKeys.some((key) => existingKeys.includes(key));
    }) || null;
  }

  function isSampleWardrobeItem(item) {
    return getWardrobeDuplicateKeys(item).some((key) => key.startsWith("sample:"));
  }

  function getWardrobeDuplicateKeys(item) {
    const keys = [];
    const knownSampleKey = SAMPLE_KEY_BY_NAME.get(normalizeDuplicateText(item.analysis?.name));
    if (item.sampleKey) keys.push(`sample:${item.sampleKey}`);
    if (knownSampleKey) keys.push(`sample:${knownSampleKey}`);
    if (item.originalFileName === "sample" && item.analysis?.name) keys.push(`sample:${createSampleKeyFromName(item.analysis.name)}`);
    if (item.imageFingerprint) keys.push(`image:${item.imageFingerprint}`);
    if (item.image) keys.push(`image-data:${item.image}`);
    return keys;
  }

  function setUploadStatusForDuplicate(duplicateItem) {
    setUploadState({ text: `Already saved as ${duplicateItem.analysis?.name || "an item"}.`, tone: "error" });
  }

  async function readImageFileAsOptimizedDataUrl(file) {
    const originalDataUrl = await readFileAsDataUrl(file);
    return resizeImageDataUrl(originalDataUrl, MAX_ANALYSIS_IMAGE_DIMENSION, ANALYSIS_IMAGE_QUALITY);
  }

  function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(new Error("Could not read this file."));
      reader.readAsDataURL(file);
    });
  }

  function resizeImageDataUrl(sourceDataUrl, maxDimension, imageQuality) {
    return new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => {
        const scale = Math.min(1, maxDimension / Math.max(image.width, image.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(image.width * scale));
        canvas.height = Math.max(1, Math.round(image.height * scale));

        const canvasContext = canvas.getContext("2d");
        canvasContext.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", imageQuality));
      };
      image.onerror = () => reject(new Error("Could not optimize this image."));
      image.src = sourceDataUrl;
    });
  }

  async function createImageFingerprint(imageDataUrl) {
    if (!window.crypto?.subtle) {
      return imageDataUrl;
    }

    const encodedImage = new TextEncoder().encode(imageDataUrl);
    const digest = await window.crypto.subtle.digest("SHA-256", encodedImage);
    return Array.from(new Uint8Array(digest))
      .map((byte) => byte.toString(16).padStart(2, "0"))
      .join("");
  }

  async function sendJsonToBackend(endpointPath, payload) {
    const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}${endpointPath}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(result.error || result.detail || "Request failed.");
    }
    return result;
  }

  async function fetchStoredWardrobeItems() {
    const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/wardrobe-items`);
    const result = await response.json().catch(() => []);
    if (!response.ok) {
      throw new Error(result.error || result.detail || "Could not load wardrobe items from R2.");
    }
    return Array.isArray(result) ? result : [];
  }

  async function authenticatedFetch(url, options) {
    options = Object.assign({}, options, { credentials: "include" });
    const method = String(options.method || "GET").toUpperCase();
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
      const csrfResponse = await fetch(`${BACKEND_API_BASE_URL}/api/auth/csrf`, { credentials: "include" });
      const csrf = await csrfResponse.json();
      options.headers = Object.assign({}, options.headers, { "X-XSRF-TOKEN": csrf.token });
    }
    const response = await fetch(url, options);
    if (response.status === 401 && !url.includes("/api/auth/")) window.location.reload();
    return response;
  }

  function AuthScreen(props) {
    const [mode, setMode] = React.useState("login");
    const [username, setUsername] = React.useState("");
    const [password, setPassword] = React.useState("");
    const [status, setStatus] = React.useState("");
    const [busy, setBusy] = React.useState(false);

    async function submit(event) {
      event.preventDefault(); setBusy(true); setStatus("");
      try {
        const response = await authenticatedFetch(`${BACKEND_API_BASE_URL}/api/auth/${mode}`, {
          method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username, password })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.detail || result.error || "Could not sign in.");
        props.onAuthenticated(result);
      } catch (error) { setStatus(error.message); } finally { setBusy(false); }
    }

    return createElement("main", { className: "auth-shell" },
      createElement("section", { className: "auth-card" },
        createElement("div", { className: "brand auth-brand" }, createElement("div", { className: "brand-mark" }, "AW"), createElement("h1", null, "AI Wardrobe")),
        createElement("div", null, createElement("h2", null, mode === "login" ? "Welcome back" : "Create your wardrobe"), createElement("p", null, "Your clothes stay private to your account.")),
        createElement("form", { onSubmit: submit, className: "auth-form" },
          createElement("label", null, "Username", createElement("input", { value: username, onChange: (e) => setUsername(e.target.value), autoComplete: "username", minLength: 3, maxLength: 40, required: true, autoCapitalize: "none" })),
          createElement("label", null, "Password", createElement("input", { type: "password", value: password, onChange: (e) => setPassword(e.target.value), autoComplete: mode === "login" ? "current-password" : "new-password", minLength: 10, maxLength: 128, required: true })),
          status ? createElement("p", { className: "auth-error", role: "alert" }, status) : null,
          createElement("button", { className: "primary-button auth-submit", disabled: busy, type: "submit" }, busy ? "Please wait..." : mode === "login" ? "Log in" : "Create account")
        ),
        createElement("button", { className: "auth-switch", type: "button", onClick: () => { setMode(mode === "login" ? "register" : "login"); setStatus(""); } }, mode === "login" ? "New here? Create an account" : "Already have an account? Log in")
      )
    );
  }

  function createSampleWardrobeItemImage(name, category, colorA, colorB) {
    const canvas = document.createElement("canvas");
    canvas.width = 320;
    canvas.height = 360;
    const ctx = canvas.getContext("2d");
    const bg = ctx.createLinearGradient(0, 0, 320, 360);
    bg.addColorStop(0, "#f8fafc");
    bg.addColorStop(1, "#e2e8f0");
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, 320, 360);
    ctx.fillStyle = "#dbe4ea";
    ctx.beginPath();
    ctx.arc(250, 74, 54, 0, Math.PI * 2);
    ctx.fill();

    const garment = ctx.createLinearGradient(70, 55, 220, 260);
    garment.addColorStop(0, colorA);
    garment.addColorStop(1, colorB);
    ctx.fillStyle = garment;
    ctx.strokeStyle = "rgba(23, 32, 42, .22)";
    ctx.lineWidth = 5;

    if (category === "top") {
      drawFilledPolygon(ctx, [[158, 72], [128, 106], [98, 72], [62, 124], [92, 148], [102, 132], [102, 282], [218, 282], [218, 132], [228, 148], [258, 124], [222, 72], [184, 50]]);
      ctx.fillStyle = "#ffffff55";
      ctx.fillRect(139, 76, 40, 104);
    } else {
      drawFilledPolygon(ctx, [[108, 48], [218, 48], [236, 288], [174, 288], [158, 138], [136, 288], [74, 288], [94, 48]]);
      ctx.fillStyle = "#ffffff35";
      ctx.fillRect(154, 58, 8, 226);
    }

    ctx.fillStyle = "#334155";
    ctx.font = "700 15px Arial";
    ctx.textAlign = "center";
    ctx.fillText(name, 160, 334);
    return canvas.toDataURL("image/png");
  }

  function drawFilledPolygon(ctx, points) {
    ctx.beginPath();
    points.forEach(([x, y], index) => {
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
  }

  ReactDOM.createRoot(document.getElementById("root")).render(createElement(WardrobeStudioApp));
})();
