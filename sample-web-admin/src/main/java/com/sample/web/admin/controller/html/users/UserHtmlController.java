package com.sample.web.admin.controller.html.users;

import java.util.List;

import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;

import com.sample.domain.dto.UploadFile;
import com.sample.domain.dto.User;
import com.sample.domain.dto.common.DefaultPageable;
import com.sample.domain.dto.common.ID;
import com.sample.domain.service.user.UserService;
import com.sample.web.base.controller.html.AbstractHtmlController;
import com.sample.web.base.util.MultipartFileUtils;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * ユーザー管理
 */
@Controller
@RequestMapping("/users")
@SessionAttributes(types = { SearchUserForm.class, UserForm.class })
@Slf4j
public class UserHtmlController extends AbstractHtmlController {

    @Autowired
    UserFormValidator userFormValidator;

    @Autowired
    UserService userService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @ModelAttribute("userForm")
    public UserForm userForm() {
        return new UserForm();
    }

    @ModelAttribute("searchUserForm")
    public SearchUserForm searchUserForm() {
        return new SearchUserForm();
    }

    @InitBinder("userForm")
    public void validatorBinder(WebDataBinder binder) {
        binder.addValidators(userFormValidator);
    }

    @Override
    public String getFunctionName() {
        return "A_User";
    }

    /**
     * 登録画面 初期表示
     *
     * @param model
     * @return
     */
    @GetMapping("/new")
    public String newUser(Model model) {
        model.addAttribute("userForm", new UserForm());
        return "users/new";
    }

    /**
     * 登録処理
     *
     * @param form
     * @param result
     * @return
     */
    @PostMapping("/new")
    public String newUser(@Validated @ModelAttribute("userForm") UserForm form, BindingResult result) {
        // 入力チェックエラーがある場合は、元の画面にもどる
        if (result.hasErrors()) {
            return "users/new";
        }

        // 入力値からDTOを作成する
        val inputUser = modelMapper.map(form, User.class);
        val password = form.getPassword();

        // パスワードをハッシュ化する
        inputUser.setPassword(passwordEncoder.encode(password));

        // 登録する
        val createdUser = userService.create(inputUser);

        return "redirect:/users/show/" + createdUser.getId().getValue();
    }

    /**
     * 一覧画面 初期表示
     *
     * @param model
     * @return
     */
    @GetMapping("/find")
    public String findUser(@ModelAttribute SearchUserForm form, Model model) {
        // 入力値を詰め替える
        val where = modelMapper.map(form, User.class);

        // 10件区切りで取得する
        val pages = userService.findAll(where, form);

        // 画面に検索結果を渡す
        model.addAttribute("pages", pages);

        return "users/find";
    }

    /**
     * 検索結果
     * 
     * @param form
     * @param result
     * @return
     */
    @PostMapping("/find")
    public String findUser(@Validated @ModelAttribute("searchUserForm") SearchUserForm form, BindingResult result) {
        // 入力チェックエラーがある場合は、元の画面にもどる
        if (result.hasErrors()) {
            return "users/find";
        }

        return "redirect:/users/find";
    }

    /**
     * 詳細画面
     *
     * @param userId
     * @return
     */
    @GetMapping("/show/{userId}")
    public String showUser(@PathVariable Integer userId, Model model) {
        // 1件取得する
        val user = userService.findById(ID.of(userId));
        model.addAttribute("user", user);

        if (user.getUploadFile() != null) {
            // 添付ファイルを取得する
            val uploadFile = user.getUploadFile();

            // Base64デコードして解凍する
            val base64data = uploadFile.getContent().toBase64();
            val sb = new StringBuilder().append("data:image/png;base64,").append(base64data);

            model.addAttribute("image", sb.toString());
        }

        return "users/show";
    }

    /**
     * 編集画面 初期表示
     * 
     * @param userId
     * @param form
     * @return
     */
    @GetMapping("/edit/{userId}")
    public String editUser(@PathVariable Integer userId, @ModelAttribute UserForm form) {
        // セッションから取得できる場合は、読み込み直さない
        if (form.getId() == null) {
            // 1件取得する
            val user = userService.findById(ID.of(userId));

            // 取得したDtoをFromに詰め替える
            modelMapper.map(user, form);
        }

        return "users/new";
    }

    /**
     * 編集画面 更新処理
     * 
     * @param form
     * @param result
     * @param userId
     * @param sessionStatus
     * @return
     */
    @PostMapping("/edit/{userId}")
    public String editUser(@Validated @ModelAttribute("userForm") UserForm form, BindingResult result,
            @PathVariable Integer userId, SessionStatus sessionStatus) {
        // 入力チェックエラーがある場合は、元の画面にもどる
        if (result.hasErrors()) {
            return "users/new";
        }

        // 更新対象を取得する
        val user = userService.findById(ID.of(userId));

        // 入力値を詰め替える
        modelMapper.map(form, user);

        val image = form.getUserImage();
        if (image != null && !image.isEmpty()) {
            UploadFile uploadFile = user.getUploadFile();
            if (uploadFile == null) {
                uploadFile = new UploadFile();
            }
            MultipartFileUtils.convert(image, uploadFile);
            user.setUploadFile(uploadFile);
        }

        // 更新する
        val updatedUser = userService.update(user);

        // セッションのuserFormをクリアする
        sessionStatus.setComplete();

        return "redirect:/users/show/" + updatedUser.getId().getValue();
    }

    /**
     * CSVダウンロード
     * 
     * @param filename
     * @return
     */
    @GetMapping("/download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<String> downloadCsv(@PathVariable String filename) {
        // 全件取得する
        val users = userService.findAll(new User(), new DefaultPageable(1, Integer.MAX_VALUE));

        val listType = new TypeToken<List<UserCsv>>() {
        }.getType();
        List<UserCsv> destCsvList = modelMapper.map(users.getData(), listType);

        // レスポンスを設定する
        val response = csvDownloadService.createResponseEntity(UserCsv.class, destCsvList, filename);

        return response;
    }

    /**
     * 権限テスト
     *
     * @return
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/testRole")
    public String testRole() {
        return "redirect:/";
    }
}